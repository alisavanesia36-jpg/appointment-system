# 预约管理系统

> 完整实现用户预约、服务管理、员工分配、用户管理、改期、提醒通知的 uni-app + Spring Boot 全栈项目。

---

## 一、项目简介

`appointment-system` 是一套完整可运行的「预约管理系统」全栈示例，覆盖后端 REST API + JWT 鉴权 + JPA 持久化，以及前端 uni-app Vue3 多端应用（H5 / 小程序 / App）。当前主线版本 **v2.2-appointment-notification**，完整实现了 24 小时 / 1 小时预约时间提醒的站内通知能力。

项目起源于毕业设计 / 接单练手场景，强调**单一进程、最小依赖、稳定业务规则**，不引入 Redis、MQ、WebSocket 等超出当前规模的中间件。

---

## 二、项目功能

| 模块 | 已实现版本 | 简介 |
|------|-----------|------|
| 安全基线 | v1.0 | BCrypt 密码 + Spring Security 链 |
| 角色权限 | v1.1 | USER / ADMIN 两级 |
| 用户注册 | v1.2 | 自注册入口 |
| 前端创建预约 | v1.3 | 用户侧预约 |
| 我的预约列表 | v1.4 | 个人预约管理 |
| 管理员写权限 | v1.5 | 管理员可写接口加固 |
| 管理员预约 | v1.6 | 后台预约列表 |
| 服务 / 员工 / 员工-服务分配管理 | v1.7.x | 后台三件套 |
| 用户管理 | v1.8 | 管理员用户 CRUD |
| 管理仪表盘 | v1.9 | 后台数据展示 |
| 可预约时间段 | v2.0 | available-slots |
| 预约改期 | v2.1 | reschedule |
| **预约提醒 + 站内通知** | **v2.2** | **当前主线** |

---

## 三、技术栈

### 后端

| 组件 | 版本 |
|------|------|
| Java | 17 |
| Spring Boot | 4.0.8 |
| Spring Data JPA | 由 Boot 引入 |
| Spring Security | 由 Boot 引入 |
| JWT (nimbus-jose-jwt) | 10.4 |
| MySQL Connector | 由 Boot 引入 |
| Lombok | 由 Boot 引入 |
| Maven | 3.9.x |

### 前端

| 组件 | 说明 |
|------|------|
| 框架 | uni-app (Vue 3) |
| IDE | HBuilderX 5.24 |
| 目标平台 | H5（同时支持 App / 小程序构建） |
| 构建工具 | Vite |
| 状态 / 路由 | Vue 3 Composition API + uni-app 内置 |
| HTTP | `@/utils/request.js` 统一封装 |

---

## 四、系统角色

| 角色 | 描述 |
|------|------|
| `USER` | 普通用户：注册、登录、浏览服务、创建/取消/改期自己的预约、查看通知 |
| `ADMIN` | 管理员：上述全部 + 服务管理 + 员工管理 + 员工-服务分配 + 用户管理 + 后台预约管理 + 后台仪表盘 |

管理员**不**能查看其他用户的通知（v2.2 明确不做此功能）。

---

## 五、核心业务流程

### 5.1 用户预约主流程

```
USER 注册 / 登录
↓
浏览服务列表 (pages/service/list)
↓
浏览员工列表 (pages/staff/list)
↓
查看可预约时间段 GET /appointments/available-slots?staffId&serviceId&date
↓
创建预约 POST /appointments
   状态 = PENDING
↓
ADMIN 在后台预约列表 confirm
   状态 = CONFIRMED
↓
   ┌──── ReminderScheduledTask 每分钟扫描 ────┐
   │  appointmentTime 距 now ≈ 24h → APPOINTMENT_24H
   │  appointmentTime 距 now ≈ 1h  → APPOINTMENT_1H
   └────────────────────────────────────────────┘
↓
ADMIN 完成预约
   状态 = COMPLETED
```

### 5.2 改期 / 取消 / 删除 联动

| 用户行为 | 状态变更 | 通知清理 |
|----------|----------|----------|
| `PUT /appointments/{id}/reschedule` | appointmentTime 修改 | 旧通知物理删除，新时间由下次定时任务扫描重新生成 |
| `PUT /appointments/{id}/cancel` | 状态 → CANCELLED | 该预约关联通知物理删除 |
| `DELETE /appointments/{id}`（ADMIN） | 行删除 | 该预约关联通知物理删除 |

---

## 六、项目目录结构

```
appointment-system/
├─ HELP.md                              # Spring Boot 默认文档（不删即可）
├─ pom.xml                              # Maven 配置
├─ mvnw / mvnw.cmd / .mvn/wrapper/      # Maven Wrapper（离线环境需要外网）
├─ src/
│  ├─ main/
│  │  ├─ java/com/example/appointmentsystem/
│  │  │  ├─ AppointmentSystemApplication.java   # 启动类（v2.2 加 @EnableScheduling）
│  │  │  ├─ config/                             # SecurityConfig / PasswordEncoderConfig
│  │  │  ├─ controller/                         # REST 控制器
│  │  │  │  ├─ AuthController.java
│  │  │  │  ├─ AppointmentController.java       # 用户侧 + ADMIN 侧预约
│  │  │  │  ├─ ServiceController.java
│  │  │  │  ├─ StaffController.java
│  │  │  │  ├─ StaffServiceMappingController.java
│  │  │  │  ├─ UserController.java
│  │  │  │  └─ NotificationController.java      # v2.2 新增
│  │  │  ├─ dto/                                # Login / User / Appointment DTO
│  │  │  ├─ entity/                             # 实体
│  │  │  │  ├─ User.java
│  │  │  │  ├─ Service.java
│  │  │  │  ├─ Staff.java
│  │  │  │  ├─ StaffServiceMapping.java
│  │  │  │  ├─ StaffServiceId.java
│  │  │  │  ├─ Appointment.java
│  │  │  │  ├─ AppointmentStatus.java
│  │  │  │  ├─ Role.java
│  │  │  │  ├─ Notification.java                # v2.2 新增
│  │  │  │  └─ NotificationType.java            # v2.2 新增
│  │  │  ├─ exception/                          # BusinessException + GlobalExceptionHandler
│  │  │  ├─ repository/                          # JPA Repository
│  │  │  │  ├─ UserRepository.java
│  │  │  │  ├─ ServiceRepository.java
│  │  │  │  ├─ StaffRepository.java
│  │  │  │  ├─ StaffServiceMappingRepository.java
│  │  │  │  ├─ AppointmentRepository.java
│  │  │  │  └─ NotificationRepository.java      # v2.2 新增
│  │  │  ├─ security/                            # JWT / SecurityUtils / Filter / Details
│  │  │  ├─ service/                             # 业务逻辑
│  │  │  │  ├─ AuthService.java
│  │  │  │  ├─ UserService.java
│  │  │  │  ├─ ServiceService.java
│  │  │  │  ├─ StaffService.java
│  │  │  │  ├─ StaffServiceMappingService.java
│  │  │  │  ├─ JwtService.java
│  │  │  │  ├─ AppointmentService.java          # v2.2 增加 NotificationService 联动
│  │  │  │  └─ NotificationService.java         # v2.2 新增
│  │  │  └─ task/
│  │  │     └─ ReminderScheduledTask.java       # v2.2 新增
│  │  └─ resources/
│  │     └─ application.properties              # dev 环境
│  └─ test/
│     ├─ java/com/example/appointmentsystem/
│     │  ├─ AppointmentSystemApplicationTests.java
│     │  ├─ controller/
│     │  │  ├─ AuthControllerTest.java
│     │  │  ├─ RoleAuthorizationTest.java
│     │  │  ├─ SecurityAuthenticationTest.java
│     │  │  ├─ ServiceStaffAuthorizationTest.java
│     │  │  ├─ UserAuthorizationTest.java
│     │  │  ├─ UserControllerTest.java
│     │  │  ├─ StaffDeleteBusinessTest.java
│     │  │  ├─ AppointmentControllerTest.java
│     │  │  ├─ AppointmentBusinessRuleTest.java
│     │  │  ├─ AppointmentIsolationTest.java
│     │  │  ├─ AppointmentAvailabilityTest.java
│     │  │  ├─ AppointmentRescheduleTest.java
│     │  │  └─ NotificationControllerTest.java  # v2.2 新增
│     │  ├─ service/
│     │  │  └─ JwtServiceTest.java
│     │  └─ task/
│     │     ├─ ReminderScheduledTaskTest.java     # v2.2 新增
│     │     └─ AppointmentNotificationLinkageTest.java  # v2.2 新增
│     └─ resources/
│        └─ application-test.properties          # v2.2 增加 spring.task.scheduling.enabled=false
├─ frontend/                                # uni-app 前端
│  ├─ api/
│  │  ├─ auth.js
│  │  ├─ user.js
│  │  ├─ service.js
│  │  ├─ staff.js
│  │  ├─ appointment.js
│  │  └─ notification.js                    # v2.2 新增
│  ├─ pages/
│  │  ├─ index/index.vue                     # 首页（v2.2 增加消息通知入口 + 未读角标）
│  │  ├─ login/login.vue
│  │  ├─ register/register.vue
│  │  ├─ service/list.vue
│  │  ├─ staff/list.vue
│  │  ├─ appointment/
│  │  │  ├─ create.vue
│  │  │  ├─ list.vue
│  │  │  └─ reschedule.vue
│  │  ├─ notification/list.vue               # v2.2 新增
│  │  └─ admin/
│  │     ├─ dashboard/index.vue
│  │     ├─ appointment/list.vue
│  │     ├─ service/list.vue
│  │     ├─ staff/list.vue
│  │     ├─ staff-service/list.vue
│  │     └─ user/list.vue
│  ├─ utils/
│  │  ├─ auth.js
│  │  └─ request.js                          # H5 /backend-api 代理 + JWT
│  ├─ pages.json                             # v2.2 增加 pages/notification/list
│  ├─ App.vue / main.js / manifest.json
│  └─ vite.config.js
## 七、后端启动方法

### 7.1 前置依赖

| 组件 | 版本要求 |
|------|----------|
| JDK | 17+ |
| Maven | 3.9.x |
| MySQL | 8.x 或 5.7+ |
| Node.js | 仅前端 H5 需要 |

### 7.2 启动 MySQL

```bash
# 启动 MySQL 服务（具体命令取决于系统）
# Linux
sudo systemctl start mysql
# macOS
brew services start mysql

# 建库（首次）
CREATE DATABASE appointment_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

`application.properties` 中默认账号密码 `root` / `root`，`server.port=8080`。如需调整请同步改两处：

- `src/main/resources/application.properties`
- `src/test/resources/application-test.properties`（仅测试环境用，账号密码与 dev 一致）

### 7.3 启动后端

```bash
cd appointment-system

# 方式 A：本机已有 Maven
mvn test                  # 跑 162 个测试
mvn spring-boot:run       # 启动应用，监听 :8080

# 方式 B：使用项目自带的 Maven Wrapper
./mvnw.cmd test           # Windows
./mvnw test               # Linux / macOS
```

注意：Maven Wrapper（`mvnw` / `mvnw.cmd`）首次运行会尝试从 `repo.maven.apache.org` 下载 Maven 发行版，离线 / 内网环境会失败。推荐本机安装 Maven 后用 `mvn` 命令。

---

## 八、MySQL 配置方法

### 8.1 dev 环境（`application.properties`）

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/appointment_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=root
spring.jpa.hibernate.ddl-auto=update
```

`ddl-auto=update` 让 Hibernate 启动时自动建表 / 加列 / 加约束。

### 8.2 test 环境（`application-test.properties`）

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/appointment_system_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=root
spring.jpa.hibernate.ddl-auto=create-drop
spring.task.scheduling.enabled=false
app.reminder.scheduler.enabled=false
```

`create-drop` 在测试启动时创建表、测试结束销毁；`createDatabaseIfNotExist=true` 让 MySQL 自动建库。两行 v2.2 配置阻断真实定时任务在测试期间执行。

---

## 九、前端启动方法（HBuilderX）

```bash
# 1. 用 HBuilderX 5.24 打开 frontend/ 目录
# 2. 右键任意 .vue 文件 → 运行 → 运行到浏览器 → Chrome
# 3. HBuilderX 自动启动 H5，访问 http://localhost:5173
```

也可使用 HBuilderX 自带的 Node 内置脚本预览（无需自行安装 npm 包）。项目不依赖 `package.json` 第三方库。

---

## 十、H5 访问

| URL | 用途 |
|-----|------|
| `http://localhost:5173` | H5 入口 |
| `http://localhost:5173/#/pages/login/login` | 直接登录页 |
| `http://localhost:8080` | 后端 REST API |

---

## 十一、测试账号说明

> 测试账号为 **手动注册 / 后台创建**，本项目不内置默认账号。

| 账号 | 密码 | 角色 | 说明 |
|------|------|------|------|
| admin001 | 123456 | ADMIN | 项目当前主测试管理员账号，由admin 后台「用户管理」手工创建 |
| 普通用户 | 由 UI 自注册 | USER | 通过 `pages/register/register` 创建，role 强制 USER |

修改用户角色由 ADMIN 通过「用户管理 → 编辑」完成。

---

## 十二、API 核心接口说明

完整接口文档以 controller 注释为准；以下是核心路径：

### 12.1 认证

| Method | Path | Body | 描述 |
|--------|------|------|------|
| POST | `/auth/register` | `{ username, password, phone }` | 用户注册（强制 USER） |
| POST | `/auth/login` | `{ username, password }` | 登录返回 JWT |
| GET | `/users/me` | — | 当前登录用户信息 |

### 12.2 预约

| Method | Path | 描述 |
|--------|------|------|
| GET | `/appointments/my` | 当前用户全部预约 |
| POST | `/appointments` | 创建预约（状态 PENDING） |
| PUT | `/appointments/{id}/cancel` | 取消（自身） |
| PUT | `/appointments/{id}/reschedule` | v2.1 改期 |
| GET | `/appointments/available-slots?staffId&serviceId&date` | v2.0 可预约时间段 |
| GET | `/appointments` | ADMIN 全部预约 |
| PUT | `/appointments/{id}/confirm` | ADMIN 确认 → CONFIRMED |
| PUT | `/appointments/{id}/complete` | ADMIN 完成 → COMPLETED |
| DELETE | `/appointments/{id}` | ADMIN 删除 |

### 12.3 服务 / 员工 / 分配 / 用户（ADMIN）

| Method | Path |
|--------|------|
| GET / POST / PUT / DELETE | `/services`、`/staff`、`/staff-services`、`/users` |

### 12.4 通知（v2.2）

| Method | Path | 描述 |
|--------|------|------|
| GET | `/notifications` | 当前用户通知列表（倒序） |
| GET | `/notifications/unread-count` | 当前用户未读数量 → `{ count: N }` |
| PUT | `/notifications/{id}/read` | 标记单条已读（非本人 403；幂等） |
| PUT | `/notifications/read-all` | 当前用户全部已读 → `{ updated: N }` |

ADMIN 也**不能**访问其他用户的通知（v2.2 第一阶段明确不实现）。

---

## 十三、JWT 登录说明

- 登录成功返回 JWT（`{ token, ... }`）
- Token 默认 1 小时过期（`jwt.expiration=3600`，单位秒）
- 前端 `utils/request.js` 自动从 `uni.getStorageSync('token')` 读取，每次请求自动加 `Authorization: Bearer <token>`
- 401 响应自动清空本地 token 并 reLaunch 到登录页

---

## 十四、预约状态流转

```
PENDING  ──── confirm  ───→  CONFIRMED  ──── complete ───→  COMPLETED
   │                              │
   └──── cancel ────→ CANCELLED   └──── cancel ────→ CANCELLED
```

| 状态 | 提醒生成 | 改期 | 取消 |
|------|----------|------|------|
| PENDING | 否 | 是 | 是 |
| CONFIRMED | 是（24h / 1h） | 是 | 是 |
| CANCELLED | 否 | 否 | — |
| COMPLETED | 否 | 否 | 否 |

---

## 十五、可预约时间段机制（v2.0）

- 入口：`GET /appointments/available-slots?staffId=X&serviceId=Y&date=YYYY-MM-DD[&excludeAppointmentId=Z]`
- 服务时长由后端从 service 表读取，前端不可信也不应传
- 返回该员工当日 09:00–18:00 营业时间内、所有未冲突的起始时间字符串数组
- 改期场景（v2.1）通过 `excludeAppointmentId` 参数把自己原预约剔除出 busy 列表

---

## 十六、改期机制（v2.1）

- 入口：`PUT /appointments/{id}/reschedule`
- 请求体只允许 `{ appointmentTime }`，serviceId/staffId/userId/status 一律被后端从数据库原值覆盖入参，杜绝前端篡改
- 状态白名单：PENDING / CONFIRMED
- v2.2 联动：成功后物理删除旧通知，由定时任务按新 appointmentTime 重新生成

---

## 十七、通知提醒机制（v2.2）

- `ReminderScheduledTask` 每分钟执行一次（`@Scheduled(cron="0 * * * * *")`）
- 扫描两个窗口：
  - 24h：`appointmentTime ∈ [now+24h-5min, now+24h+5min)`
  - 1h：`appointmentTime ∈ [now+1h-5min, now+1h+5min)`
- 只处理 CONFIRMED 状态的预约
- 幂等三层保护：应用层 `existsByRelatedAppointmentIdAndType` 预检 + `catch DataIntegrityViolationException` + DB `UNIQUE(related_appointment_id, type)`
- 用户只能查看自己的通知；管理员也不能查看其他用户通知

---

## 十八、项目测试情况

| 版本 | 测试总数 | 通过 | 新增 |
|------|---------|------|------|
| v2.1（改期） | 138 | 138 / 138 | +18 |
| v2.2（通知） | **162** | **162 / 162** | **+24** |

`+24` 分布：

- `NotificationControllerTest`：7 个（权限 / 401 / 跨用户隔离 / 未读数 / 单条已读 / 全部已读）
- `ReminderScheduledTaskTest`：13 个（CONFIRMED 24h/1h、PENDING/CANCELLED/COMPLETED 跳过、跨用户隔离、同一预约同时存在 24h+1h、Clock 窗口外/内、DB UNIQUE 兜底）
- `AppointmentNotificationLinkageTest`：4 个（改期清理、改期后新时间重生成、取消清理、ADMIN delete 清理）

运行命令：

```bash
mvn test
```

期望输出末尾：

```
Tests run: 162, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 十九、部署说明

详见 [`docs/deployment/README.md`](docs/deployment/README.md)。生产部署需要：

1. MySQL 8.x 服务器
2. 后端 Java 17 运行环境（推荐 `java -jar` 打包）
3. 前端 HBuilderX 发行 H5 静态文件（Nginx / 静态托管）
4. 反向代理层（Nginx / Spring Cloud Gateway）将 H5 请求反向代理到后端

---

## 二十、项目截图位置

截图统一放置在 [`docs/screenshots/`](docs/screenshots/)。当前为空目录，由 H5 实际页面人工截图后命名归类：

| 页面 | 建议文件名 |
|------|-----------|
| 登录 | `login.png` |
| 注册 | `register.png` |
| 首页 | `home.png` |
| 服务列表 | `service-list.png` |
| 员工列表 | `staff-list.png` |
| 创建预约 | `appointment-create.png` |
| 我的预约 | `appointment-list.png` |
| 预约改期 | `appointment-reschedule.png` |
| 消息通知（v2.2） | `notification-list.png` |
| 管理仪表盘（v1.9） | `admin-dashboard.png` |
| 预约管理 | `admin-appointment.png` |
| 服务管理 | `admin-service.png` |
| 员工管理 | `admin-staff.png` |
| 员工-服务分配 | `admin-staff-service.png` |
| 用户管理 | `admin-user.png` |

---

## 许可证

本项目为练手 / 毕设示例代码，可自由参考实现思路。