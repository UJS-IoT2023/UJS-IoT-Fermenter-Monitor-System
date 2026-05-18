# Redis + WebSocket 实时数据实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Spring Boot 后端实现 Redis 细粒度缓存（1小时）+ WebSocket 实时推送 + 定时下采样到 PostgreSQL

**Architecture:**
- AMQP 消息到达后同时写入 Redis（Sorted Set，保留1小时）和广播到 WebSocket
- 定时任务每 20 分钟从 Redis 聚合数据写入 PostgreSQL
- HTTP API 分三层：latest（Redis 最新）、realtime（Redis 1小时）、history（PostgreSQL 分页）

**Tech Stack:** Spring Boot 4.0.6, Spring Data Redis (Lettuce), Spring WebSocket + STOMP, Spring Scheduling, PostgreSQL, Qpid JMS

---

## 文件清单

| 文件 | 操作 | 职责 |
|------|------|------|
| `pom.xml` | 修改 | 添加 spring-boot-starter-data-redis |
| `application.yml` | 修改 | 添加 Redis 连接配置 |
| `FermenterStatusDTO.java` | 新建 | WebSocket/HTTP 通用 DTO |
| `RedisService.java` | 新建 | Redis Sorted Set 操作封装 |
| `WebSocketConfig.java` | 新建 | STOMP + WebSocket 配置 |
| `WebSocketService.java` | 新建 | SimpMessagingTemplate 广播封装 |
| `FermenterStatusService.java` | 修改 | 新增 Redis 写入/查询、历史数据查询 |
| `AmqpMessageListener.java` | 修改 | 改用 FermenterStatusService 的新方法 |
| `DownsampleTask.java` | 新建 | 定时任务：Redis → PostgreSQL 下采样 |
| `FermenterStatusHistorical.java` | 新建 | PostgreSQL 下采样实体（可选共用现有 entity） |
| `FermenterStatusRepository.java` | 修改 | 新增历史数据查询方法 |
| `FermenterStatusController.java` | 修改 | 新增 /latest、/realtime、/history 端点 |

---

## Task 1: 添加 Redis 依赖

**Files:**
- Modify: `server/pom.xml:78`（在 `</dependencies>` 前插入）

- [ ] **Step 1: 添加 Redis 依赖**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

---

## Task 2: 添加 Redis 配置

**Files:**
- Modify: `server/src/main/resources/application.yml`

- [ ] **Step 1: 添加 Redis 配置节**

```yaml
spring:
  data:
    redis:
      host: "${REDIS_HOST:localhost}"
      port: ${REDIS_PORT:6379}
      password: "${REDIS_PASSWORD:}"
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

---

## Task 3: 创建 FermenterStatusDTO

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/dto/FermenterStatusDTO.java`

- [ ] **Step 1: 创建 DTO**

```java
package cn.arorms.fms.server.dto;

import cn.arorms.fms.server.enums.ControlMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FermenterStatusDTO implements Serializable {
    private String deviceName;
    private float temperature;
    private float phValue;
    private float dissolvedOxygen;
    private float foamLevel;
    private float addAcid;
    private float addAlkali;
    private float cooling;
    private float heating;
    private float stirring;
    private ControlMode controlMode;
    private Instant timestamp;
}
```

---

## Task 4: 创建 RedisService

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/services/RedisService.java`

- [ ] **Step 1: 创建 RedisService**

```java
package cn.arorms.fms.server.services;

import cn.arorms.fms.server.dto.FermenterStatusDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisService {

    private static final String KEY_PREFIX = "fermenter:status:";
    private static final long ONE_HOUR_MS = 60 * 60 * 1000L;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public RedisService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private String key(String deviceName) {
        return KEY_PREFIX + deviceName;
    }

    public void saveStatus(FermenterStatusDTO dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            long timestamp = dto.getTimestamp() != null
                    ? dto.getTimestamp().toEpochMilli()
                    : System.currentTimeMillis();
            String k = key(dto.getDeviceName());
            redisTemplate.opsForZSet().add(k, json, timestamp);
            // 裁剪 1 小时前的数据
            redisTemplate.opsForZSet().removeRangeByScore(k, 0, timestamp - ONE_HOUR_MS);
        } catch (JsonProcessingException e) {
            log.error("Redis 序列化失败: deviceName={}", dto.getDeviceName(), e);
        }
    }

    public FermenterStatusDTO getLatest(String deviceName) {
        String k = key(deviceName);
        Set<String> result = redisTemplate.opsForZSet().reverseRange(k, 0, 0);
        if (result == null || result.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(result.iterator().next(), FermenterStatusDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Redis 反序列化失败: deviceName={}", deviceName, e);
            return null;
        }
    }

    public List<FermenterStatusDTO> getLatestAllDevices() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<FermenterStatusDTO> result = new ArrayList<>();
        for (String k : keys) {
            String deviceName = k.substring(KEY_PREFIX.length());
            FermenterStatusDTO latest = getLatest(deviceName);
            if (latest != null) {
                result.add(latest);
            }
        }
        return result;
    }

    public List<FermenterStatusDTO> getRealtimeData(String deviceName, long fromTimestamp, long toTimestamp) {
        String k = key(deviceName);
        Set<String> raw = redisTemplate.opsForZSet().rangeByScore(k, fromTimestamp, toTimestamp);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<FermenterStatusDTO> result = new ArrayList<>();
        for (String json : raw) {
            try {
                result.add(objectMapper.readValue(json, FermenterStatusDTO.class));
            } catch (JsonProcessingException e) {
                log.error("Redis 反序列化失败", e);
            }
        }
        return result;
    }

    public List<FermenterStatusDTO> getLast20Minutes(String deviceName) {
        long now = System.currentTimeMillis();
        long twentyMinutesAgo = now - 20 * 60 * 1000L;
        return getRealtimeData(deviceName, twentyMinutesAgo, now);
    }

    public Set<String> getAllDeviceNames() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null) {
            return Collections.emptySet();
        }
        Set<String> deviceNames = new HashSet<>();
        for (String k : keys) {
            deviceNames.add(k.substring(KEY_PREFIX.length()));
        }
        return deviceNames;
    }
}
```

---

## Task 5: 修改 FermenterStatusService

**Files:**
- Modify: `server/src/main/java/cn/arorms/fms/server/services/FermenterStatusService.java`

新增 3 个方法：`processAndSaveToRedis`、`getLatestAll`、`getRealtimeData`

- [ ] **Step 1: 添加新方法到 FermenterStatusService**

在类末尾添加以下方法：

```java
public void processAndSaveToRedis(String jsonString) throws Exception {
    JsonNode rootNode = objectMapper.readTree(jsonString);

    String deviceName = rootNode.path("deviceName").asText();
    JsonNode items = rootNode.path("items");

    if (items.isMissingNode()) {
        log.warn("消息中缺少 items 字段: deviceName={}", deviceName);
        return;
    }

    FermenterStatusDTO dto = FermenterStatusDTO.builder()
            .deviceName(deviceName)
            .temperature(getFloat(items, "temperature"))
            .phValue(getFloat(items, "phValue"))
            .dissolvedOxygen(getFloat(items, "dissolvedOxygen"))
            .foamLevel(getFloat(items, "foamLevel"))
            .addAcid(getFloat(items, "addAcid"))
            .addAlkali(getFloat(items, "addAlkali"))
            .cooling(getFloat(items, "cooling"))
            .heating(getFloat(items, "heating"))
            .stirring(getFloat(items, "stirring"))
            .controlMode(items.has("controlMode")
                    ? ControlMode.fromCode(items.get("controlMode").get("value").asInt())
                    : null)
            .timestamp(Instant.now())
            .build();

    long gmtCreate = rootNode.path("gmtCreate").asLong(0);
    if (gmtCreate > 0) {
        dto.setTimestamp(Instant.ofEpochMilli(gmtCreate));
    }

    redisService.saveStatus(dto);
    log.info("数据已存入 Redis: deviceName={}", deviceName);
}

private float getFloat(JsonNode items, String field) {
    if (items.has(field)) {
        return (float) items.get(field).get("value").asDouble();
    }
    return 0f;
}

public List<FermenterStatusDTO> getLatestAllDevices() {
    return redisService.getLatestAllDevices();
}

public List<FermenterStatusDTO> getRealtimeData(String deviceName, long fromTimestamp, long toTimestamp) {
    return redisService.getRealtimeData(deviceName, fromTimestamp, toTimestamp);
}
```

同时在类顶部添加字段：
```java
private final RedisService redisService;

@Autowired
public FermenterStatusService(FermenterStatusRepository fermenterStatusRepository,
                              ObjectMapper objectMapper,
                              RedisService redisService) {
    this.fermenterStatusRepository = fermenterStatusRepository;
    this.objectMapper = objectMapper;
    this.redisService = redisService;
}
```

---

## Task 6: 创建 WebSocketConfig

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/configs/WebSocketConfig.java`

- [ ] **Step 1: 创建 WebSocketConfig**

```java
package cn.arorms.fms.server.configs;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单内存消息代理，订阅地址前缀
        config.enableSimpleBroker("/topic");
        // 服务端收到消息的地址前缀
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 连接端点，前端连接 ws://host/api/ws
        registry.addEndpoint("/api/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
```

---

## Task 7: 创建 WebSocketService

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/services/WebSocketService.java`

- [ ] **Step 1: 创建 WebSocketService**

```java
package cn.arorms.fms.server.services;

import cn.arorms.fms.server.dto.FermenterStatusDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WebSocketService {

    public static final String TOPIC = "/topic/fermenter-status";

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(FermenterStatusDTO dto) {
        messagingTemplate.convertAndSend(TOPIC, dto);
        log.debug("WebSocket 广播成功: deviceName={}", dto.getDeviceName());
    }
}
```

---

## Task 8: 修改 AmqpMessageListener

**Files:**
- Modify: `server/src/main/java/cn/arorms/fms/server/amqp/AmqpMessageListener.java`

- [ ] **Step 1: 改用 FermenterStatusService 的 Redis 方法和 WebSocketService**

```java
@Override
public void onMessage(Message message) {
    try {
        if (message instanceof BytesMessage bytesMessage) {
            byte[] body = new byte[(int) bytesMessage.getBodyLength()];
            bytesMessage.readBytes(body);
            String jsonString = new String(body, StandardCharsets.UTF_8);

            log.info("收到设备数据: {}", jsonString);
            fermenterStatusService.processAndSaveToRedis(jsonString);

            // 同时广播 WebSocket（从 Redis 取出刚写入的数据，或直接构造 DTO）
            // 为简化，这里从 jsonString 解析出 deviceName，
            // 调用 RedisService.getLatest(deviceName) 取最新一条广播
            JsonNode rootNode = objectMapper.readTree(jsonString);
            String deviceName = rootNode.path("deviceName").asText();
            FermenterStatusDTO latest = redisService.getLatest(deviceName);
            if (latest != null) {
                webSocketService.broadcast(latest);
            }
        }
        message.acknowledge();
    } catch (Exception e) {
        log.error("处理 AMQP 消息失败", e);
    }
}
```

需要新增注入：
```java
private final RedisService redisService;
private final WebSocketService webSocketService;
private final ObjectMapper objectMapper;  // 从 MessageListener 构造注入
```

**注意**：为避免循环依赖（FermenterStatusService → RedisService → 各自），WebSocket 广播逻辑直接放在 AmqpMessageListener 中，通过 RedisService 取最新数据。

---

## Task 9: 创建 DownsampleTask 定时任务

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/tasks/DownsampleTask.java`

- [ ] **Step 1: 创建 DownsampleTask**

```java
package cn.arorms.fms.server.tasks;

import cn.arorms.fms.server.dto.FermenterStatusDTO;
import cn.arorms.fms.server.entities.FermenterStatus;
import cn.arorms.fms.server.enums.ControlMode;
import cn.arorms.fms.server.repositories.FermenterStatusRepository;
import cn.arorms.fms.server.services.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors.Collectors;

@Slf4j
@Component
public class DownsampleTask {

    private final RedisService redisService;
    private final FermenterStatusRepository repository;

    @Autowired
    public DownsampleTask(RedisService redisService, FermenterStatusRepository repository) {
        this.redisService = redisService;
        this.repository = repository;
    }

    // 每小时 0分、20分、40分执行: 0 0,20,40 * * * *
    @Scheduled(cron = "0 0,20,40 * * * *")
    public void downsample() {
        log.info("开始执行下采样任务");
        Instant now = Instant.now();

        for (String deviceName : redisService.getAllDeviceNames()) {
            List<FermenterStatusDTO> dataPoints = redisService.getLast20Minutes(deviceName);
            if (dataPoints.isEmpty()) {
                continue;
            }

            int count = dataPoints.size();
            Map<String, List<FermenterStatusDTO>> grouped = dataPoints.stream()
                    .collect(Collectors.groupingBy(FermenterStatusDTO::getDeviceName));

            for (Map.Entry<String, List<FermenterStatusDTO>> entry : grouped.entrySet()) {
                List<FermenterStatusDTO> list = entry.getValue();
                FermenterStatus avg = new FermenterStatus();
                avg.setDeviceName(entry.getKey());
                avg.setTemperature(avgFloat(list, FermenterStatusDTO::getTemperature));
                avg.setPhValue(avgFloat(list, FermenterStatusDTO::getPhValue));
                avg.setDissolvedOxygen(avgFloat(list, FermenterStatusDTO::getDissolvedOxygen));
                avg.setFoamLevel(avgFloat(list, FermenterStatusDTO::getFoamLevel));
                avg.setAddAcid(avgFloat(list, FermenterStatusDTO::getAddAcid));
                avg.setAddAlkali(avgFloat(list, FermenterStatusDTO::getAddAlkali));
                avg.setCooling(avgFloat(list, FermenterStatusDTO::getCooling));
                avg.setHeating(avgFloat(list, FermenterStatusDTO::getHeating));
                avg.setStirring(avgFloat(list, FermenterStatusDTO::getStirring));
                // controlMode 取最新
                avg.setControlMode(list.get(list.size() - 1).getControlMode());
                avg.setTimestamp(now);

                repository.save(avg);
                log.info("下采样写入 PostgreSQL: deviceName={}, 原始数据条数={}", entry.getKey(), count);
            }
        }
        log.info("下采样任务完成");
    }

    private float avgFloat(List<FermenterStatusDTO> list, java.util.function.ToFloatFunction<FermenterStatusDTO> getter) {
        return (float) list.stream().mapToDouble(dto -> getter.applyAsFloat(dto)).average().orElse(0);
    }
}
```

**注意**：`@EnableScheduling` 需要在主应用类或配置类上存在，检查 `ServerApplication.java` 是否有。

---

## Task 10: 修改 FermenterStatusController 新增端点

**Files:**
- Modify: `server/src/main/java/cn/arorms/fms/server/controllers/FermenterStatusController.java`

- [ ] **Step 1: 添加 3 个新端点**

```java
@GetMapping("/latest")
public ResponseEntity<List<FermenterStatusDTO>> getLatest() {
    return ResponseEntity.ok(fermenterStatusService.getLatestAllDevices());
}

@GetMapping("/realtime")
public ResponseEntity<List<FermenterStatusDTO>> getRealtime(
        @RequestParam(required = false) String deviceName) {
    long now = System.currentTimeMillis();
    long oneHourAgo = now - 60 * 60 * 1000L;
    if (deviceName != null && !deviceName.isBlank()) {
        return ResponseEntity.ok(fermenterStatusService.getRealtimeData(deviceName, oneHourAgo, now));
    }
    // 无 deviceName 时返回所有设备的最新数据列表（各设备取最新一条）
    return ResponseEntity.ok(fermenterStatusService.getLatestAllDevices());
}

@GetMapping("/history")
public ResponseEntity<Page<FermenterStatus>> getHistory(
        @RequestParam(required = false) String deviceName,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
    Page<FermenterStatus> result;
    if (deviceName != null && !deviceName.isBlank()) {
        result = repository.findByDeviceName(deviceName, pageable);
    } else {
        result = repository.findAll(pageable);
    }
    return ResponseEntity.ok(result);
}
```

同时在顶部添加 import：
```java
import cn.arorms.fms.server.dto.FermenterStatusDTO;
import java.util.List;
```

---

## Task 11: 修改 FermenterStatusRepository

**Files:**
- Modify: `server/src/main/java/cn/arorms/fms/server/repositories/FermenterStatusRepository.java`

- [ ] **Step 1: 添加按设备名分页查询方法**

```java
Page<FermenterStatus> findByDeviceName(String deviceName, Pageable pageable);
```

---

## Task 12: 启用定时任务

**Files:**
- Modify: `server/src/main/java/cn/arorms/fms/server/ServerApplication.java`

- [ ] **Step 1: 添加 @EnableScheduling**

```java
@SpringBootApplication
@EnableScheduling
public class ServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
```

---

## Task 13: Redis 配置类

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/configs/RedisConfig.java`

- [ ] **Step 1: 创建 RedisConfig**

Spring Boot 自动配置 Redis，但需要显式声明 `RedisTemplate<String, String>` 的 Bean：

```java
package cn.arorms.fms.server.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
```

---

## 实施顺序

1. Task 1 — pom.xml 加 Redis 依赖
2. Task 2 — application.yml 加 Redis 配置
3. Task 13 — RedisConfig.java
4. Task 3 — FermenterStatusDTO
5. Task 4 — RedisService
6. Task 5 — FermenterStatusService 修改（注入 RedisService）
7. Task 6 — WebSocketConfig
8. Task 7 — WebSocketService
9. Task 8 — AmqpMessageListener 修改
10. Task 9 — DownsampleTask
11. Task 10 — FermenterStatusController 修改
12. Task 11 — FermenterStatusRepository 修改
13. Task 12 — ServerApplication 加 @EnableScheduling

---

## 依赖关系图

```
pom.xml (Redis依赖)
    │
    ▼
application.yml + RedisConfig
    │
    ├──▶ FermenterStatusDTO (无依赖)
    │
    ├──▶ RedisService (注入 RedisTemplate)
    │
    ├──▶ WebSocketConfig + WebSocketService (注入 SimpMessagingTemplate)
    │
    ├──▶ FermenterStatusService (注入 RedisService)
    │
    ├──▶ AmqpMessageListener (注入 FermenterStatusService + RedisService + WebSocketService)
    │
    ├──▶ DownsampleTask (注入 RedisService + FermenterStatusRepository)
    │
    ├──▶ FermenterStatusController (注入 FermenterStatusService + FermenterStatusRepository)
    │
    └──▶ ServerApplication (@EnableScheduling)
```
