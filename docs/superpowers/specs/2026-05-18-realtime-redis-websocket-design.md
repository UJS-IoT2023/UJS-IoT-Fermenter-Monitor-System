# Fermenter Monitor System — Redis + WebSocket 实时数据架构设计

**日期**: 2026-05-18
**状态**: 已批准

---

## 1. 背景与目标

发酵罐监控系统需要同时满足：
- **实时监控**：5秒粒度的细粒度数据，前端实时展示 + 折线图
- **历史分析**：长期数据存储，以20分钟为间隔的下采样数据供历史查询

## 2. 架构概览

```
[MQTT设备] 5秒/条
    │
    ▼
[阿里云 AMQP]
    │
    ▼
[AmqpMessageListener]
    ├──▶ FermenterStatusService.processAndSaveToRedis(jsonString)
    │        ZADD fermenter:status:{deviceName} timestamp jsonString
    │        ZREMRANGEBYSCORE 裁剪 1 小时前数据
    │
    └──▶ WebSocketService.broadcast(message)
             SimpMessagingTemplate.convertAndSend("/topic/fermenter-status", DTO)
                        │
                        ▼
              [WebSocket Broker]
                        │
              ┌─────────┴─────────┐
         [实时面板]          [实时面板]
         (订阅同一topic)    (订阅同一topic)
```

---

## 3. 数据存储设计

### 3.1 Redis — 细粒度实时数据（1小时）

**Key模式**: `fermenter:status:{deviceName}`
**数据结构**: Sorted Set
- Score: timestamp (epoch 毫秒)
- Member: JSON 序列化的状态对象

每条消息写入时，同时裁剪 1 小时前的旧数据：
```
ZADD fermenter:status:{deviceName} {timestamp} {jsonString}
ZREMRANGEBYSCORE fermenter:status:{deviceName} 0 {timestamp - 1小时}
```

**数据量估算**（10台设备，每台5秒/条）：
- 每台设备: 720 条/小时
- 10台设备: 7200 条/小时
- 数据量极小，Redis 完全可承载

### 3.2 PostgreSQL — 下采样历史数据

**表**: `fermenter_status_historical`（或共用现有 `fermenter_status` 表，通过 timestamp 区分）

每 20 分钟聚合一次，字段取窗口内平均值：
```sql
INSERT INTO fermenter_status_historical (device_name, temperature, ph_value, ...)
SELECT device_name,
       AVG(temperature),
       AVG(ph_value),
       ...
FROM (
    SELECT * FROM redis_zrangebyscore_...
) GROUP BY device_name
```

---

## 4. 定时下采样任务

### 4.1 执行时机

Cron: `0 0,20,40 * * * *`
即每小时的 0分、20分、40分（10:00、10:20、10:40...）

### 4.2 聚合逻辑

1. 对每台设备，从 Redis 获取最近 20 分钟的所有数据
2. 计算各数值字段的算术平均值
3. 写入 PostgreSQL

### 4.3 字段说明

| 字段 | 来源 | 说明 |
|------|------|------|
| deviceName | 消息 | 设备名称 |
| temperature | AVG | 温度平均值 |
| phValue | AVG | pH平均值 |
| dissolvedOxygen | AVG | 溶氧平均值 |
| foamLevel | AVG | 泡沫层平均值 |
| addAcid | AVG | 加酸量平均值 |
| addAlkali | AVG | 加碱量平均值 |
| cooling | AVG | 冷却量平均值 |
| heating | AVG | 加热量平均值 |
| stirring | AVG | 搅拌量平均值 |
| controlMode | LATEST | 取最新值（非数值） |
| timestamp | WINDOW_END | 20分钟窗口结束时间 |

---

## 5. WebSocket + STOMP 设计

### 5.1 配置

- **订阅地址**: `/topic/fermenter-status`
- **消息格式**: `FermenterStatusDTO`
- **广播模式**: 广播给所有连接的客户端

### 5.2 消息流程

```
AMQP消息到达
    → FermenterStatusService.processAndSaveToRedis()
    → WebSocketService.broadcast(FermenterStatusDTO)
          → SimpMessagingTemplate.convertAndSend("/topic/fermenter-status", dto)
                → 所有订阅该topic的WebSocket客户端收到消息
```

### 5.3 前端初始加载

用户连接 WebSocket 后，前端先调 HTTP 接口获取当前状态：
```
GET /api/fermenter-status/latest
```
返回各设备最新一条数据，前端展示面板初始状态。

---

## 6. API 设计

### 6.1 获取最新状态（HTTP）

```
GET /api/fermenter-status/latest
```
从 Redis 获取每台设备的最新一条记录，返回 `List<FermenterStatusDTO>`

### 6.2 获取1小时折线图数据（HTTP）

```
GET /api/fermenter-status/realtime?deviceName={deviceName}
```
参数:
- `deviceName` (可选，默认所有设备)

从 Redis Sorted Set 获取最近1小时数据：
```
ZRANGEBYSCORE fermenter:status:{deviceName} {now-1小时} {now}
```

### 6.3 历史数据查询（HTTP）

```
GET /api/fermenter-status/history?deviceName={deviceName}&page=0&size=100
```
数据来源: PostgreSQL（20分钟下采样数据），按 timestamp 倒序分页。

---

## 7. DTO 设计

### FermenterStatusDTO（实时/历史通用）

```java
public class FermenterStatusDTO {
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

## 8. 组件清单

| 组件 | 职责 |
|------|------|
| `AmqpMessageListener` | 接收AMQP消息，触发处理流程 |
| `FermenterStatusService` | Redis写入 + 广播 |
| `RedisService` (新) | Redis Sorted Set 操作 |
| `WebSocketConfig` | STOMP + WebSocket 配置 |
| `WebSocketService` | 通过 SimpMessagingTemplate 广播 |
| `DownsampleTask` | 定时任务，每20分钟下采样到PG |
| `FermenterStatusController` | HTTP API（latest, realtime, history） |
| `FermenterStatusRepository` | PostgreSQL 访问 |

---

## 9. 数据流程总结

```
MQTT 5秒上报
    │
    ▼
AMQP ──────────────────────────────────┐
    │                                   │
    ▼                                   ▼
AmqpMessageListener            FermenterStatusService
    │                                   │
    ├── processAndSaveToRedis()         │
    │       ZADD → Redis               │
    │       ZREMRANGEBYSCORE            │
    │                                   │
    └── broadcast()                     │
            SimpMessagingTemplate       │
            → /topic/fermenter-status  │
                                        │
                                    WebSocket推送
                                    → 前端实时面板

每20分钟:
DownsampleTask
    ZRANGEBYSCORE → 计算平均
    → PostgreSQL (fermenter_status_historical)
```
