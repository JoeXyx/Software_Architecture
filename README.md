# SA 项目集合

本项目包含三个实践项目，展示了不同的软件架构模式和开发技术。

## 📁 项目结构

```
SA/
├── first practice/          # 聊天系统
│   ├── chat-client/         # 客户端应用
│   └── chat-server/         # 服务端应用
├── second practice/         # 三层架构数据分析系统
│   ├── BLL/                 # 业务逻辑层
│   ├── DAL/                 # 数据访问层
│   └── ui/                  # 用户界面层（Web应用）
└── third practice/          # 邮件管理系统
    ├── ems/                 # 邮件服务模块
    └── mainwindow.cpp       # C++ 主窗口
```

---

## 🚀 项目一：聊天系统 (First Practice)

### 项目简介
基于消息队列（MQ）的实时聊天系统，采用客户端-服务端架构，支持用户登录、实时聊天和文件上传功能。

### 技术栈
- **语言**: Java
- **UI框架**: Java Swing
- **消息队列**: RabbitMQ
- **数据库**: MySQL（通过 DBHelper）

### 项目结构

#### 客户端 (chat-client)
- `MainClient.java` - 客户端主入口
- `ui/LoginFrame.java` - 登录界面
- `ui/ChatFrame.java` - 聊天界面
- `mq/MQConnection.java` - 消息队列连接管理
- `util/FileUploader.java` - 文件上传工具

#### 服务端 (chat-server)
- `ServerMain.java` - 服务端主入口
- `listener/UserListener.java` - 用户消息监听器
- `mq/MQManager.java` - 消息队列管理器
- `service/UserService.java` - 用户服务
- `db/DBHelper.java` - 数据库操作助手
- `util/FileServer.java` - 文件服务器

### 运行方式
1. 启动服务端：运行 `ServerMain.java`
2. 启动客户端：运行 `MainClient.java`
3. 使用登录界面进行用户认证
4. 登录成功后进入聊天界面进行实时通信

---

## 📊 项目二：三层架构数据分析系统 (Second Practice)

### 项目简介
基于三层架构（UI-BLL-DAL）的数据分析系统，使用消息队列实现层间通信，提供门店数据分析和可视化功能。

### 技术栈
- **语言**: Java
- **框架**: Spring Boot
- **消息队列**: RabbitMQ
- **数据分析**: Apache Commons Math3
- **JSON处理**: FastJSON2

### 项目结构

#### UI层 (ui)
- `UiApplication.java` - Spring Boot 应用入口
- `controller/AnalysisController.java` - 数据分析控制器
- `controller/StoreController.java` - 门店数据控制器
- `mq/MQManager.java` - 消息队列管理器
- `mq/ResponsePool.java` - 响应池管理
- `config/WebConfig.java` - Web配置

#### BLL层 (业务逻辑层)
- `BLLService.java` - 业务逻辑服务
- `MQManager_BLL.java` - 消息队列管理器
- `main.java` - BLL层启动入口

**核心功能**：
- 门店营业时长统计分析（均值、中位数、众数、标准差等）
- 开店扩张趋势分析（线性回归预测）
- 门店地理分布离散度分析
- 营业时长与地理位置相关性分析

#### DAL层 (数据访问层)
- `DALService.java` - 数据访问服务
- `MQManager.java` - 消息队列管理器
- `main.java` - DAL层启动入口

### 运行方式
1. 启动 DAL 层：运行 `DAL/main.java`
2. 启动 BLL 层：运行 `BLL/main.java`
3. 启动 UI 层：运行 `UiApplication.java`
4. 通过 Web 界面访问数据分析功能

### 数据分析功能
- ✅ 营业时长统计（平均值、中位数、众数、标准差、四分位数等）
- ✅ 扩张趋势分析（月度统计、线性回归、下月预测）
- ✅ 地理分布分析（中心点、标准差、四分位距）
- ✅ 省份营业时长对比分析

---

## 📧 项目三：邮件管理系统 (Third Practice)

### 项目简介
基于 Spring Boot 的邮件管理系统，支持通过 REST API 和 SOAP Web Service 发送邮件，集成腾讯企业邮箱服务。

### 技术栈
- **语言**: Java, C++
- **框架**: Spring Boot
- **邮件服务**: 腾讯企业邮箱
- **Web服务**: SOAP, REST API
- **JSON处理**: Jackson

### 项目结构

#### 邮件服务模块 (ems)
- `EmsApplication.java` - Spring Boot 应用入口
- `controller/EmailController.java` - 邮件控制器
  - `/api/email/send` - REST API 发送邮件
  - `/api/email/sendEmail` - SOAP 服务调用
- `service/EmailServiceImpl.java` - 邮件服务实现
- `service/TencentEmailSender.java` - 腾讯邮箱发送器
- `util/EmailServicePublisher.java` - 邮件服务发布器
- `util/TencentMailConfig.java` - 腾讯邮箱配置
- `ws/EmailService.java` - Web Service 接口

#### C++ 组件
- `mainwindow.cpp` - C++ 主窗口（可能用于邮件客户端界面）

### 功能特性
- ✅ REST API 邮件发送
- ✅ SOAP Web Service 支持
- ✅ 腾讯企业邮箱集成
- ✅ 邮件模板支持
- ✅ 异步邮件发送

### 运行方式
1. 启动 Spring Boot 应用：运行 `EmsApplication.java`
2. 通过 REST API 发送邮件：`POST /api/email/send`
3. 通过 SOAP 服务发送邮件：`POST /api/email/sendEmail`

### API 示例

#### REST API
```json
POST /api/email/send
{
  "to": "recipient@example.com",
  "subject": "邮件主题",
  "content": "邮件内容",
  "name": "发件人名称"
}
```

---

## 🛠️ 技术特点

### 消息队列架构
- 项目一和项目二均采用 RabbitMQ 实现异步通信
- 支持请求-响应模式
- 实现了解耦和可扩展性

### 分层架构
- 项目二采用经典的三层架构（UI-BLL-DAL）
- 清晰的职责分离
- 便于维护和扩展

### 数据分析能力
- 使用 Apache Commons Math3 进行统计分析
- 支持描述性统计、回归分析等
- 提供数据可视化支持

### 多种通信协议
- REST API
- SOAP Web Service
- 消息队列

---

## 📝 开发环境要求

- **JDK**: 1.8 或更高版本
- **构建工具**: Maven 或 Gradle（根据项目配置）
- **消息队列**: RabbitMQ
- **数据库**: MySQL（项目一需要）
- **IDE**: IntelliJ IDEA 或 Eclipse（推荐）

---

## 🔧 配置说明

### 消息队列配置
各项目中的 MQ 管理器需要配置 RabbitMQ 连接信息：
- 主机地址
- 端口（默认 5672）
- 用户名和密码
- 虚拟主机

### 数据库配置
项目一的 `DBHelper.java` 需要配置数据库连接信息。

### 邮件服务配置
项目三的 `TencentMailConfig.java` 需要配置腾讯企业邮箱的认证信息。

---

## 📚 学习要点

1. **消息队列应用**: 学习如何使用 RabbitMQ 实现异步通信
2. **分层架构设计**: 理解三层架构的设计原则和实现方式
3. **数据分析**: 掌握使用数学库进行数据统计分析
4. **Web服务开发**: 学习 REST API 和 SOAP 服务的开发
5. **Spring Boot**: 掌握 Spring Boot 框架的基本使用

---

## 📄 许可证

本项目仅用于学习和实践目的。

---

## 👤 作者

SA 项目集合

---

## 📞 联系方式

如有问题或建议，欢迎提交 Issue 或 Pull Request。

