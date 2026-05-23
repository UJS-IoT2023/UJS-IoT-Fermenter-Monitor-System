# 远程+本地双模式控制实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为发酵罐模拟网关添加远程操控（前端→后端→阿里云IoT→MQTT→网关）和本地自动控制（网关本地模糊控制）两种模式。

**Architecture:**
- **REMOTE 模式**: 前端控制面板 → 后端 REST API → 阿里云 IoT OpenAPI → MQTT property set → 网关
- **LOCAL 模式**: 网关本地模糊控制器自动调整 5 个控制参数（5 档: 0/25/50/75/100）
- 后端通过阿里云 IoT REST API（OpenAPI）下发属性设置，而非 AMQP（AMQP 只能消费）

**Tech Stack:** Spring Boot 4 (Java 21), React 19, TypeScript/Gateway Simulator, Alibaba IoT REST API, MQTT

---

## 文件结构

```
server/src/main/java/cn/arorms/fms/server/
├── controllers/
│   └── FermenterControlController.java      [新建] 控制命令接收
├── services/
│   └── AliyunIotOpenApiService.java         [新建] 阿里云IoT OpenAPI调用
├── dto/
│   └── FermenterControlCommand.java          [新建] 控制命令DTO
├── configs/
│   └── AliyunOpenApiConfig.java              [新建] OpenAPI配置（REST客户端）

gateway-simulator/src/
├── FuzzyController.ts                        [新建] 本地模糊控制器
├── index.ts                                   [修改] 启动本地控制逻辑

web/src/
├── App.jsx                                    [修改] 添加控制UI（5个滑块+模式切换）
```

---

## 前置了解

### 阿里云 IoT OpenAPI（后端下发控制用）
阿里云 IoT 提供了 REST API 来设置设备属性：
- 接口: `https://iot.cn-shanghai.aliyuncs.com`
- API: `SetDeviceProperty` / `InvokeThingService`
- 认证: AccessKey + AccessSecret HMAC-SHA1
- 用途: 后端通过此 API 将控制命令发给阿里云，再由阿里云通过 MQTT 下发到设备

### 模糊控制逻辑（网关本地）
5 个参数：addAcid, addAlkali, cooling, heating, stirring，每档 0/25/50/75/100。
模糊控制器根据传感器读数（temperature, phValue）自动调整：
- pH 高 → 加酸档位升高
- pH 低 → 加碱档位升高
- 温度高 → 冷却档位升高
- 温度低 → 加热档位升高
- 始终保持搅拌在中间档位

---

## 任务列表

### Task 1: 后端 - 阿里云 IoT OpenAPI 服务

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/configs/AliyunOpenApiConfig.java`
- Create: `server/src/main/java/cn/arorms/fms/server/services/AliyunIotOpenApiService.java`
- Modify: `server/src/main/resources/application.yml`（添加 OpenAPI 相关配置）
- Test: 通过 curl 验证设备属性下发

- [ ] **Step 1: 添加 application.yml OpenAPI 配置**

```yaml
aliyun:
  iot:
    openapi:
      region: "${ALIYUN_REGION:cn-shanghai}"
      access-key: "${ALIYUN_ACCESS_KEY}"
      access-secret: "${ALIYUN_ACCESS_SECRET}"
      iot-instance-id: "${ALIYUN_IOT_INSTANCE_ID}"
```

- [ ] **Step 2: 创建 AliyunOpenApiConfig**

```java
// RestTemplate 配置，支持 HMAC-SHA1 签名
@Configuration
public class AliyunOpenApiConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

- [ ] **Step 3: 创建 AliyunIotOpenApiService**

```java
@Service
public class AliyunIotOpenApiService {
    // 使用阿里云 IoT OpenAPI SetDeviceProperty 接口
    // https://help.aliyun.com/zh/iot/user-guide/invoke-the-setdeviceproperty-interface
    public void setDeviceProperty(String iotId, Map<String, Object> properties) {
        // 1. 构建请求 URL: https://iot.cn-shanghai.aliyuncs.com/?Action=SetDeviceProperty&Format=JSON&Version=2018-01-20
        // 2. HMAC-SHA1 签名
        // 3. POST 请求发送 properties
    }
}
```

- [ ] **Step 4: 验证服务可用性**

Run: `curl` 或通过日志验证后端能成功调用 OpenAPI

---

### Task 2: 后端 - 控制命令 API

**Files:**
- Create: `server/src/main/java/cn/arorms/fms/server/dto/FermenterControlCommand.java`
- Create: `server/src/main/java/cn/arorms/fms/server/controllers/FermenterControlController.java`
- Modify: `server/src/main/java/cn/arorms/fms/server/services/FermenterStatusService.java`（添加根据 deviceName 获取 iotId 的方法）
- Modify: `server/src/main/java/cn/arorms/fms/server/repositories/FermenterConnectionRedisRepository.java`（添加获取 iotId 的方法）

- [ ] **Step 1: 创建 FermenterControlCommand DTO**

```java
@Data
public class FermenterControlCommand {
    private String deviceName;       // 设备名
    private Integer addAcid;         // 0/25/50/75/100
    private Integer addAlkali;
    private Integer cooling;
    private Integer heating;
    private Integer stirring;
    private Integer controlMode;     // 0=LOCAL, 1=REMOTE
}
```

- [ ] **Step 2: 添加获取 iotId 的 Redis 方法**

```java
// FermenterConnectionRedisRepository.java
public String getIotIdByDeviceName(String deviceName) {
    HashOperations<String, String, String> ops = redisTemplate.opsForHash();
    return ops.get("fermenter:device:" + deviceName, "iotId");
}
```

- [ ] **Step 3: 创建 FermenterControlController**

```java
@RestController
@RequestMapping("/api/fermenter-control")
public class FermenterControlController {
    @Autowired
    private AliyunIotOpenApiService openApiService;
    @Autowired
    private FermenterConnectionRedisRepository connectionRepo;

    @PostMapping("/command")
    public ResponseEntity<Void> sendCommand(@RequestBody FermenterControlCommand command) {
        // 1. 获取 iotId
        String iotId = connectionRepo.getIotIdByDeviceName(command.getDeviceName());
        if (iotId == null) {
            return ResponseEntity.notFound().build();
        }
        // 2. 构建 property map
        Map<String, Object> props = new HashMap<>();
        if (command.getAddAcid() != null) props.put("addAcid", command.getAddAcid());
        if (command.getAddAlkali() != null) props.put("addAlkali", command.getAddAlkali());
        if (command.getCooling() != null) props.put("cooling", command.getCooling());
        if (command.getHeating() != null) props.put("heating", command.getHeating());
        if (command.getStirring() != null) props.put("stirring", command.getStirring());
        if (command.getControlMode() != null) props.put("controlMode", command.getControlMode());
        // 3. 调用 OpenAPI
        openApiService.setDeviceProperty(iotId, props);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 4: 测试 API**

Run: `curl -X POST http://localhost:8080/api/fermenter-control/command -H "Content-Type: application/json" -d '{"deviceName":"xxx","controlMode":1}'`

---

### Task 3: 前端 - 控制面板 UI

**Files:**
- Modify: `web/src/App.jsx`

- [ ] **Step 1: 添加控制模式切换和 5 个滑块组件**

在 `DeviceDetail` 中，将控制参数区域从只读 progress bar 改为可交互的滑块：

```jsx
// 控制模式切换
<div className="flex items-center gap-4 mb-4">
  <span className="text-gray-500">控制模式</span>
  <div className="flex gap-2">
    <button
      onClick={() => onControlModeChange(0)}
      className={`px-3 py-1 rounded-full text-sm font-medium ${
        status?.controlMode === 'LOCAL' ? 'bg-green-500 text-white' : 'bg-gray-200 text-gray-700'
      }`}
    >
      本地自动
    </button>
    <button
      onClick={() => onControlModeChange(1)}
      className={`px-3 py-1 rounded-full text-sm font-medium ${
        status?.controlMode === 'REMOTE' ? 'bg-blue-500 text-white' : 'bg-gray-200 text-gray-700'
      }`}
    >
      远程控制
    </button>
  </div>
</div>

// 5个滑块 (0/25/50/75/100)
const controlParams = [
  { key: 'addAcid', label: '加酸', color: '#F59E0B' },
  { key: 'addAlkali', label: '加碱', color: '#6366F1' },
  { key: 'cooling', label: '冷却', color: '#06B6D4' },
  { key: 'heating', label: '加热', color: '#EC4899' },
  { key: 'stirring', label: '搅拌', color: '#84CC16' },
]

controlParams.map(({ key, label, color }) => (
  <div key={key} className="bg-gray-50 rounded-lg p-3">
    <div className="flex justify-between text-xs text-gray-500 mb-1">
      <span>{label}</span>
      <span>{controlValues[key]}%</span>
    </div>
    <input
      type="range"
      min="0" max="100" step="25"
      value={controlValues[key]}
      onChange={(e) => onControlChange(key, Number(e.target.value))}
      disabled={status?.controlMode === 'LOCAL'}
      className="w-full h-2 rounded-lg cursor-pointer disabled:opacity-50"
      style={{ accentColor: color }}
    />
    <div className="flex justify-between text-xs text-gray-400 mt-1">
      <span>0</span><span>25</span><span>50</span><span>75</span><span>100</span>
    </div>
  </div>
))
```

- [ ] **Step 2: 添加 controlValues 状态和 onControlChange / onControlModeChange 回调**

```jsx
const [controlValues, setControlValues] = useState({
  addAcid: 0, addAlkali: 0, cooling: 0, heating: 0, stirring: 0
})

const onControlChange = useCallback((key, value) => {
  setControlValues(prev => ({ ...prev, [key]: value }))
  // 发送命令到后端
  fetch(`${API_BASE}/api/fermenter-control/command`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceName: selectedDevice, [key]: value })
  })
}, [selectedDevice])

const onControlModeChange = useCallback((mode) => {
  fetch(`${API_BASE}/api/fermenter-control/command`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceName: selectedDevice, controlMode: mode })
  })
}, [selectedDevice])
```

- [ ] **Step 3: 同步 status 中的控制参数到 controlValues**

```jsx
useEffect(() => {
  if (status) {
    setControlValues({
      addAcid: status.addAcid ?? 0,
      addAlkali: status.addAlkali ?? 0,
      cooling: status.cooling ?? 0,
      heating: status.heating ?? 0,
      stirring: status.stirring ?? 0,
    })
  }
}, [status])
```

- [ ] **Step 4: 测试 UI**

Run: `cd web && npm run dev`
Expected: 能看到控制模式切换按钮和 5 个滑块，切换到 REMOTE 模式时可调节参数

---

### Task 4: 网关 - 本地模糊控制器

**Files:**
- Create: `gateway-simulator/src/FuzzyController.ts`
- Modify: `gateway-simulator/src/Fermenter.ts`（集成模糊控制器）
- Modify: `gateway-simulator/src/index.ts`（初始化模糊控制）

- [ ] **Step 1: 创建 FuzzyController.ts**

```typescript
export class FuzzyController {
  // 5档: 0, 25, 50, 75, 100
  private levels = [0, 25, 50, 75, 100];

  // 根据传感器读数计算控制参数
  adjustControls(sensors: { temperature: number; phValue: number }): {
    addAcid: number;
    addAlkali: number;
    cooling: number;
    heating: number;
    stirring: number;
  } {
    // pH 模糊控制
    // pH > 7.5: 加酸档位升高
    // pH < 6.5: 加碱档位升高
    // pH 在 6.5-7.5: 维持当前档位
    let addAcid = 50;
    let addAlkali = 50;
    if (sensors.phValue > 7.5) {
      addAcid = Math.min(100, addAcid + 25);
      addAlkali = Math.max(0, addAlkali - 25);
    } else if (sensors.phValue < 6.5) {
      addAcid = Math.max(0, addAcid - 25);
      addAlkali = Math.min(100, addAlkali + 25);
    }

    // 温度模糊控制
    // temperature > 40: 冷却升高，加热降低
    // temperature < 30: 加热升高，冷却降低
    let cooling = 50;
    let heating = 50;
    if (sensors.temperature > 40) {
      cooling = Math.min(100, cooling + 25);
      heating = Math.max(0, heating - 25);
    } else if (sensors.temperature < 30) {
      cooling = Math.max(0, cooling - 25);
      heating = Math.min(100, heating + 25);
    }

    // 搅拌保持中间档位
    const stirring = 50;

    return { addAcid, addAlkali, cooling, heating, stirring };
  }
}
```

- [ ] **Step 2: 修改 Fermenter.ts 集成模糊控制器**

```typescript
import { FuzzyController } from './FuzzyController';

export class Fermenter {
  // ... existing fields ...
  private fuzzyController: FuzzyController;
  private localControlInterval: NodeJS.Timeout | null = null;

  // 在构造函数或 connect() 中初始化
  this.fuzzyController = new FuzzyController();

  // 修改 updateSensorValues，在 LOCAL 模式下自动调整控制参数
  updateSensorValues(): void {
    // ... existing sensor fluctuation logic ...

    // LOCAL 模式：调用模糊控制器
    if (this.properties.controlMode === 0) { // LOCAL
      const controls = this.fuzzyController.adjustControls({
        temperature: this.properties.temperature,
        phValue: this.properties.phValue,
      });
      this.properties.addAcid = controls.addAcid;
      this.properties.addAlkali = controls.addAlkali;
      this.properties.cooling = controls.cooling;
      this.properties.heating = controls.heating;
      this.properties.stirring = controls.stirring;
    }
  }

  // disconnect 时清理 interval
  disconnect(): void {
    if (this.localControlInterval) {
      clearInterval(this.localControlInterval);
    }
    // ... existing disconnect logic ...
  }
}
```

- [ ] **Step 3: 验证本地控制逻辑**

启动网关，选中一个 fermenter，手动 setProperty controlMode=0，观察 addAcid/addAlkali/cooling/heating 是否自动变化

---

### Task 5: 本地模式时前端 UI 同步

**Files:**
- Modify: `web/src/App.jsx`

- [ ] **Step 1: LOCAL 模式下禁用滑块，显示本地控制指示**

在滑块容器上添加 `disabled` 样式，在 LOCAL 模式时显示"本地自动控制中"提示

---

## 验证计划

1. **REMOTE 模式测试**:
   - 前端切换到 REMOTE 模式
   - 拖动滑块到 75
   - 查看网关日志是否收到新的 addAcid 属性值

2. **LOCAL 模式测试**:
   - 前端切换到 LOCAL 模式
   - 网关自动开始调整 5 个参数
   - 观察 pH/温度变化时参数是否按模糊逻辑自动调整

3. **模式切换测试**:
   - LOCAL → REMOTE: 确认滑块变为可用，参数保持当前值
   - REMOTE → LOCAL: 确认网关接管控制，前端滑块禁用

---

## 架构总结

```
[前端控制面板]
    | HTTP POST /api/fermenter-control/command
    v
[后端 Controller] → [AliyunIotOpenApiService]
    | HTTPS REST (SetDeviceProperty)
    v
[阿里云 IoT OpenAPI] → [阿里云 IoT MQTT Broker]
    | MQTT property set
    v
[网关 Fermenter.ts handlePropertySet()] → 更新 properties
    |
    +-- REMOTE: 前端直接控制，属性被覆盖
    |
    +-- LOCAL: fuzzyController.adjustControls() 自动覆盖
```
