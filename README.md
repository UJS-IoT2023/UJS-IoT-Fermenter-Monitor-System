*江苏大学 2026 物联网控制技术实验四*

![wakapi.arorms.cn](https://wakapi.arorms.cn/api/badge/Cacciatore/interval:any/project:UJS-IoT-Fermenter-Monitor-System)

# MQTT 中继

使用阿里云物联网平台。

# 物联网平台

使用 Java Spring Boot 4.0.6，数据库为 PostgreSQL。负责与 MQTT 中继通信，

# 模拟终端（网关）

利用 Typescript CLI 模式，以物联网终端网关的身份模拟发酵罐终端设备：

1. 作为模拟网关，可以添加和管理模拟发酵罐终端。
2. 可以获取发酵罐的详细数据与操作发酵罐终端。