# 设备状态20分钟历史数据方案

## 现状
- 前端先调用 `GET /api/fermenter-status/{deviceName}/latest` 获取最后状态
- 再通过 WebSocket 订阅实时数据
- 折线图只显示 WebSocket 推送的数据（最多几十个点）

## 目标
前端一次性获取设备过去20分钟的历史数据，用于折线图显示，上方状态卡片直接使用最后一个数据点。

## 技术方案

### 后端改动
**新增接口：** `GET /api/fermenter-status/{deviceName}/last-20-minutes`
- 返回该设备最近20分钟内的所有状态记录（已存在于 Redis）
- 利用现有的 `FermenterStatusRedisRepository.getLast20Minutes()` 方法
- 返回 List<FermenterStatusDto>

**Controller 改动：** 在 `FermenterStatusController` 添加新接口

### 前端改动
**查询逻辑调整：**
- 选中设备时，调用 `GET /api/fermenter-status/{deviceName}/last-20-minutes`
- 将返回的列表直接设置到 `realtimeData` 状态
- 上方状态卡片使用列表最后一个元素（最新状态）
- WebSocket 继续监听新数据，追加到图表

**保留 WebSocket：** 实时数据推送仍然保留，用于保持实时性

## 数据流
```
Redis (20分钟数据)
    ↓
GET /last-20-minutes
    ↓
前端折线图显示
    ↓
WebSocket 追加新数据点
```

## 影响范围
- 后端：新增1个接口
- 前端：修改设备选择时的数据加载逻辑
- 不影响现有 WebSocket 订阅逻辑