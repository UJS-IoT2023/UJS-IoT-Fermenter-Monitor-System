# 设备上下线状态检测实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增设备上下线状态检测功能，支持在 AMQP 消息中区分状态消息和上下线消息，分别处理并持久化到 MySQL 和 Redis

**Architecture:**
- AMQP Listener 收到消息后根据内容判断类型：有 `status` 字段 → 上下线消息，有 `items` 字段 → 状态消息
- 上下线消息通过 `FermenterConnectionService` 处理，存 MySQL (FermenterConnectionEvent) + 更新 Redis (device:online:{deviceName})
- Redis 使用 String 类型存储设备在线状态

**Tech Stack:** Spring Boot, JPA, Redis Template, AMQP

---

## 文件结构

```
server/src/main/java/cn/arorms/fms/server/
├── entities/
│   └── FermenterConnectionEvent.java    # 新增
├── enums/
│   └── ConnectionEventType.java        # 新增
├── repositories/
│   └── FermenterConnectionEventRepository.java  # 新增
├── services/
│   ├── RedisService.java               # 修改，新增设备上下线相关方法
│   ├── FermenterStatusService.java      # 修改，新增上下线消息处理入口
│   └── FermenterConnectionService.java # 新增
└── amqp/
    └── AmqpMessageListener.java        # 修改，消息类型分流
```

---

## 实现任务

### Task 1: ConnectionEventType 枚举

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/enums/ConnectionEventType.java`

- [ ] **Step 1: 创建枚举类**

```java
package cn.arorms.fms.server.enums;

public enum ConnectionEventType {
    ONLINE(1),
    OFFLINE(0);

    private final int code;

    ConnectionEventType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ConnectionEventType fromCode(int code) {
        for (ConnectionEventType type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("Unknown ConnectionEventType code: " + code);
    }

    public static ConnectionEventType fromString(String status) {
        return "online".equalsIgnoreCase(status) ? ONLINE : OFFLINE;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add server/src/main/java/cn/arorms/fms/server/enums/ConnectionEventType.java
git commit -m "feat: add ConnectionEventType enum for device online/offline events"
```

---

### Task 2: FermenterConnectionEvent 实体类

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/entities/FermenterConnectionEvent.java`

- [ ] **Step 1: 创建实体类**

```java
package cn.arorms.fms.server.entities;

import cn.arorms.fms.server.enums.ConnectionEventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fermenter_connection_event")
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class FermenterConnectionEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceName;

    @Enumerated(EnumType.STRING)
    private ConnectionEventType eventType;

    private Instant eventTime;

    private String iotId;

    private String clientIp;

    private String productKey;
}
```

- [ ] **Step 2: 提交**

```bash
git add server/src/main/java/cn/arorms/fms/server/entities/FermenterConnectionEvent.java
git commit -m "feat: add FermenterConnectionEvent entity for connection events"
```

---

### Task 3: FermenterConnectionEventRepository

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/repositories/FermenterConnectionEventRepository.java`

- [ ] **Step 1: 创建 Repository**

```java
package cn.arorms.fms.server.repositories;

import cn.arorms.fms.server.entities.FermenterConnectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FermenterConnectionEventRepository extends JpaRepository<FermenterConnectionEvent, Long> {
}
```

- [ ] **Step 2: 提交**

```bash
git add server/src/main/java/cn/arorms/fms/server/repositories/FermenterConnectionEventRepository.java
git commit -m "feat: add FermenterConnectionEventRepository"
```

---

### Task 4: RedisService 新增设备上下线方法

**Files:**
- Modify: `server/src/main/java/cn/arorms/fms/server/services/RedisService.java:17-111` (在类末尾添加新方法)

- [ ] **Step 1: 添加常量和方法**

在 `RedisService` 类中添加：

```java
private static final String DEVICE_ONLINE_KEY_PREFIX = "device:online:";

public void saveDeviceOnlineStatus(String deviceName, boolean isOnline, String lastTime, String iotId, String clientIp) {
    String key = DEVICE_ONLINE_KEY_PREFIX + deviceName;
    Map<String, String> map = new HashMap<>();
    map.put("isOnline", String.valueOf(isOnline));
    map.put("lastTime", lastTime != null ? lastTime : "");
    map.put("iotId", iotId != null ? iotId : "");
    map.put("clientIp", clientIp != null ? clientIp : "");
    redisTemplate.opsForHash().putAll(key, map);
}

public Map<Object, Object> getDeviceOnlineStatus(String deviceName) {
    String key = DEVICE_ONLINE_KEY_PREFIX + deviceName;
    return redisTemplate.opsForHash().entries(key);
}

public Set<String> getAllOnlineDeviceNames() {
    Set<String> keys = redisTemplate.keys(DEVICE_ONLINE_KEY_PREFIX + "*");
    if (keys == null) {
        return Collections.emptySet();
    }
    Set<String> deviceNames = new HashSet<>();
    for (String k : keys) {
        deviceNames.add(k.substring(DEVICE_ONLINE_KEY_PREFIX.length()));
    }
    return deviceNames;
}
```

- [ ] **Step 2: 提交**

```bash
git add server/src/main/java/cn/arorms/fms/server/services/RedisService.java
git commit -m "feat: add device online status methods to RedisService"
```

---

### Task 5: FermenterConnectionService

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/services/FermenterConnectionService.java`

- [ ] **Step 1: 创建 Service**

```java
package cn.arorms.fms.server.services;

import cn.arorms.fms.server.entities.FermenterConnectionEvent;
import cn.arorms.fms.server.enums.ConnectionEventType;
import cn.arorms.fms.server.repositories.FermenterConnectionEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class FermenterConnectionService {

    private final FermenterConnectionEventRepository repository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @Autowired
    public FermenterConnectionService(FermenterConnectionEventRepository repository,
                                      RedisService redisService,
                                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    public FermenterConnectionEvent processAndSave(String jsonString) throws Exception {
        JsonNode rootNode = objectMapper.readTree(jsonString);

        String deviceName = rootNode.path("deviceName").asText();
        String status = rootNode.path("status").asText();
        ConnectionEventType eventType = ConnectionEventType.fromString(status);

        FermenterConnectionEvent event = new FermenterConnectionEvent();
        event.setDeviceName(deviceName);
        event.setEventType(eventType);
        event.setIotId(rootNode.path("iotId").asText(null));
        event.setClientIp(rootNode.path("clientIp").asText(null));
        event.setProductKey(rootNode.path("productKey").asText(null));

        String timeStr = rootNode.path("lastTime").asText(null);
        if (timeStr != null && !timeStr.isEmpty()) {
            event.setEventTime(Instant.now());
        } else {
            event.setEventTime(Instant.now());
        }

        repository.save(event);
        log.info("Connection event saved: deviceName={}, eventType={}", deviceName, eventType);

        boolean isOnline = eventType == ConnectionEventType.ONLINE;
        redisService.saveDeviceOnlineStatus(deviceName, isOnline, timeStr,
                event.getIotId(), event.getClientIp());

        return event;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add server/src/main/java/cn/arorms/fms/server/services/FermenterConnectionService.java
git commit -m "feat: add FermenterConnectionService for device online/offline events"
```

---

### Task 6: AmqpMessageListener 消息分流

**Files:**
- Modify: `server/src/main/java/cn/arorms/fms/server/amqp/AmqpMessageListener.java`

- [ ] **Step 1: 修改 onMessage 方法**

```java
package cn.arorms.fms.server.amqp;

import cn.arorms.fms.server.dto.FermenterStatusDTO;
import cn.arorms.fms.server.services.FermenterConnectionService;
import cn.arorms.fms.server.services.FermenterStatusService;
import cn.arorms.fms.server.services.WebSocketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.BytesMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class AmqpMessageListener implements MessageListener {

    private final FermenterStatusService fermenterStatusService;
    private final FermenterConnectionService fermenterConnectionService;
    private final WebSocketService webSocketService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AmqpMessageListener(FermenterStatusService fermenterStatusService,
                               FermenterConnectionService fermenterConnectionService,
                               WebSocketService webSocketService,
                               ObjectMapper objectMapper) {
        this.fermenterStatusService = fermenterStatusService;
        this.fermenterConnectionService = fermenterConnectionService;
        this.webSocketService = webSocketService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof BytesMessage bytesMessage) {
                byte[] body = new byte[(int) bytesMessage.getBodyLength()];
                bytesMessage.readBytes(body);
                String jsonString = new String(body, StandardCharsets.UTF_8);

                log.info("Received device data: {}", jsonString);

                JsonNode rootNode = objectMapper.readTree(jsonString);

                if (rootNode.has("status")) {
                    FermenterConnectionEvent event = fermenterConnectionService.processAndSave(jsonString);
                    log.info("Device connection event processed: deviceName={}, eventType={}",
                            event.getDeviceName(), event.getEventType());
                } else if (rootNode.has("items")) {
                    FermenterStatusDTO dto = fermenterStatusService.processAndSaveToRedis(jsonString);
                    if (dto != null) {
                        webSocketService.broadcast(dto);
                    }
                } else {
                    log.warn("Unknown message format: neither status nor items field found");
                }
            }
            message.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process AMQP message", e);
        }
    }
}
```

注意：需要添加以下 import：
```java
import cn.arorms.fms.server.services.FermenterConnectionService;
import cn.arorms.fms.server.entities.FermenterConnectionEvent;
import com.fasterxml.jackson.databind.JsonNode;
```

- [ ] **Step 2: 提交**

```bash
git add server/src/main/java/cn/arorms/fms/server/amqp/AmqpMessageListener.java
git commit -m "feat: support both status and items messages in AmqpMessageListener"
```

---

### Task 7: FermenterStatusService 添加判断方法

**Files:**
- Modify: `server/src/main/java/cn/arorms/fms/server/services/FermenterStatusService.java` (方法保持不变，但需要在 AmqpMessageListener 中做消息类型判断)

实际上 Task 6 已经通过 ObjectMapper 判断消息类型，FermenterStatusService 不需要修改。

---

### Task 8: 数据库表创建

**Files:**
- 需要执行 SQL 创建表

- [ ] **Step 1: 创建数据库表**

在 MySQL 中执行以下 SQL：

```sql
CREATE TABLE IF NOT EXISTS fermenter_connection_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name VARCHAR(255) NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    event_time TIMESTAMP NOT NULL,
    iot_id VARCHAR(255),
    client_ip VARCHAR(50),
    product_key VARCHAR(255)
);
```

- [ ] **Step 2: 提交 (可选)**

---

## 验证清单

- [ ] `ConnectionEventType` 枚举可以正确转换 "online"/"offline" 字符串
- [ ] `FermenterConnectionEvent` 实体可以正确保存到数据库
- [ ] `RedisService.saveDeviceOnlineStatus()` 可以正确存储设备在线状态
- [ ] `AmqpMessageListener` 能正确分流：`status` → `FermenterConnectionService`，`items` → `FermenterStatusService`
- [ ] 上下线消息处理后，Redis 中的 `device:online:{deviceName}` Hash 正确更新
- [ ] MySQL 中 `fermenter_connection_event` 表记录正确插入