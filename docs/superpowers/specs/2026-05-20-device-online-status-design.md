# 设备上下线状态检测设计

## 背景

MQTT 消息队列中混合了两种消息：
- **状态消息**：包含 `items` 字段，存储发酵罐传感器数据
- **上下线消息**：包含 `status` 字段，标识设备上线/下线事件

需要新增对上下线消息的处理能力。

## 整体架构

```
AMQP Listener
     │
     ├── 有 items 字段 ──────→ FermenterStatusService.processAndSaveToRedis()
     │                         (存 Redis ZSet + MySQL FermenterStatus)
     │
     └── 有 status 字段 ─────→ FermenterConnectionService.processAndSave()
                               (存 Redis String + MySQL FermenterConnectionEvent)
```

## Redis 存储

**Key**: `device:online:{deviceName}` (Hash)

| 字段 | 类型 | 说明 |
|------|------|------|
| isOnline | boolean | true = online, false = offline |
| lastTime | String | 最后一次上下线时间 |
| iotId | String | 阿里云 IoT ID |
| clientIp | String | 客户端 IP |

## 数据库表

**实体**: `FermenterConnectionEvent`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键自增 |
| deviceName | String | 设备名称 |
| eventType | Enum | ONLINE / OFFLINE |
| eventTime | Instant | 事件时间 |
| iotId | String | 阿里云 IoT ID |
| clientIp | String | 客户端 IP |
| productKey | String | 产品 Key |

**不上采样**，直接存储每次上下线事件。

## 新增组件

| 组件 | 职责 |
|------|------|
| `FermenterConnectionEvent` | 实体类 |
| `ConnectionEventType` | 枚举，ONLINE / OFFLINE |
| `FermenterConnectionEventRepository` | JPA Repository |
| `FermenterConnectionService` | 业务逻辑：解析上下线消息，持久化到 MySQL，更新 Redis |
| `RedisService` 新增方法 | `saveDeviceOnlineStatus()` / `getDeviceOnlineStatus()` |

## 消息流程

1. AMQP Listener 收到消息，先判断是否有 `status` 字段
2. 有 `status` → 调用 `FermenterConnectionService.processAndSave()`:
   - 解析 `status` 判断 ONLINE/OFFLINE
   - 保存到 MySQL (`FermenterConnectionEvent`)
   - 更新 Redis Hash (`device:online:{deviceName}`)
3. 无 `status` 但有 `items` → 现有逻辑不变

## 实现任务

1. 新增 `ConnectionEventType` 枚举 (ONLINE, OFFLINE)
2. 新增 `FermenterConnectionEvent` 实体类
3. 新增 `FermenterConnectionEventRepository`
4. `FermenterConnectionService` 新增 `processAndSave()` 方法
5. `RedisService` 新增 `saveDeviceOnlineStatus()` / `getDeviceOnlineStatus()` / `getAllOnlineDevices()`
6. `AmqpMessageListener` 改造：判断消息类型后分发到不同 Service
7. 新增 `FermenterConnectionServiceTest` 单元测试