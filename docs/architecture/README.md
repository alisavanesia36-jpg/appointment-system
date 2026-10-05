# 架构说明

> 本项目采用经典三层架构 + 单向依赖 + 最小化中间件策略。
> 实际编码围绕"单一进程、小型毕设/接单规模"展开，不引入 Redis / MQ / WebSocket。

---

## 一、整体架构

```
┌──────────────────────────────────────────────────────────────┐
│                  uni-app Vue3 (H5 / App / 小程序)              │
│                                                              │
│   pages/index/index.vue          ← 首页（v2.2 消息入口）      │
│   pages/login/login.vue                                         │
│   pages/register/register.vue                                   │
│   pages/service/list.vue                                       │
│   pages/staff/list.vue                                         │
│   pages/appointment/create.vue / list.vue / reschedule.vue      │
│   pages/notification/list.vue    ← v2.2 新增                  │
│   pages/admin/dashboard / appointment / service / staff /     │
│           staff-service / user                                  │
│                                                              │
│   api/*.js  ──HTTP──→  utils/request.js  ──代理──→ /backend-api
└──────────────────────────────────────────────────────────────┘
                              │  Vite dev proxy
                              ▼
                       http://localhost:8080
┌──────────────────────────────────────────────────────────────┐
│       Spring Boot 4.0.8 + Spring Security + JWT             │
│                                                              │
│   Controller 层                                               │
│   ├─ AuthController                                           │
│   ├─ AppointmentController                                    │
│   ├─ ServiceController / StaffController /                   │
│   │  StaffServiceMappingController                            │
│   ├─ UserController                                           │
│   └─ NotificationController    ← v2.2 新增                  │
│                                                              │
│   Service 层                                                  │
│   ├─ AuthService                                              │
│   ├─ UserService / ServiceService / StaffService /           │
│   │  StaffServiceMappingService                               │
│   ├─ JwtService                                               │
│   ├─ AppointmentService    ──→ NotificationService (v2.2)   │
│   └─ NotificationService   ← v2.2 新增                     │
│                                                              │
│   Repository 层 (Spring Data JPA)                            │
│   ├─ UserRepository / ServiceRepository / StaffRepository /  │
│   │  StaffServiceMappingRepository                            │
│   ├─ AppointmentRepository                                    │
│   └─ NotificationRepository  ← v2.2 新增                    │
│                                                              │
│   定时任务（v2.2 新增）                                        │
│   └─ ReminderScheduledTask @Scheduled(cron="0 * * * * *")  │
│                                                              │
│   Security 链                                                │
│   JwtAuthenticationFilter → CustomUserDetailsService →       │
│   SecurityUtils (取当前用户名)                              │
│                                                              │
│   异常处理                                                  │
│   BusinessException → GlobalExceptionHandler → 400           │
│   ResponseStatusException → 原生状态码（403/404 等）         │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                    MySQL 8.x                                  │
│                                                              │
│   表：users / services / staff / staff_services /            │
│       appointments / notifications (v2.2)                   │
└──────────────────────────────────────────────────────────────┘
```

---

## 二、单向依赖（防循环）

```
AppointmentService ──→ NotificationService
                              ▲
                              │
              ReminderScheduledTask ─┘
```

- `NotificationService` **不**依赖 `AppointmentService`，仅依赖 Repository
- `AppointmentService` 仅通过 `NotificationService.deleteAllByAppointmentId(id)` 物理清理关联通知

---

## 三、鉴权 / 权限

### 3.1 角色

- `USER`：普通用户
- `ADMIN`：管理员

枚举写入 DB 使用 `@Enumerated(EnumType.STRING)`，与 `Appointment.status` 保持一致。

### 3.2 JWT

- `JwtService` 使用 `nimbus-jose-jwt` 10.4
- HMAC-SHA256 签名
- 默认有效期 1 小时（`jwt.expiration=3600`，单位秒）
- Token 在登录成功时返回；前端存 `uni.setStorageSync('token')`

### 3.3 Spring Security

- `SecurityConfig` 默认 `anyRequest().authenticated()` 兜底
- v2.2 NotificationController **不修改 SecurityConfig**，依赖兜底规则
- BCrypt 密码（`PasswordEncoderConfig`）

### 3.4 前端 Token 处理

`utils/request.js` 自动：

- 从 storage 取 token
- 拼 `Authorization: Bearer <token>` 头
- 401 → 清空 storage + reLaunch 登录页
- 403 → Toast「无权限访问」
- 404 / 400 / 500 → 各自 Toast

---

## 四、前端 H5 与后端的连接

- H5 平台：`BASE_URL = '/backend-api'`，走 Vite dev proxy 转发到 `http://localhost:8080`
- 非 H5：`BASE_URL = 'http://localhost:8080'`，直连绝对地址
- 选择 `/backend-api` 前缀是为了避免与前端源码目录 `@/api/*` 冲突导致 JS 资源被误代理

---

## 五、模块依赖一览

| 模块 | 依赖 |
|------|------|
| AuthController | AuthService |
| AppointmentController | AppointmentService |
| NotificationController | NotificationService（+ SecurityUtils） |
| AppointmentService | AppointmentRepository / ServiceRepository / StaffRepository / UserRepository / StaffServiceMappingRepository / **NotificationService（v2.2 联动）** |
| NotificationService | NotificationRepository / UserRepository / ServiceRepository / StaffRepository / AppointmentRepository |
| ReminderScheduledTask | NotificationService（+ Spring `@Scheduled`） |
| JwtAuthenticationFilter | JwtService / UserRepository |

---

## 六、事务策略

| 方法 | 事务 |
|------|------|
| `NotificationService.markRead` | `@Transactional` |
| `NotificationService.markAllRead` | `@Transactional` |
| `NotificationService.deleteAllByAppointmentId` | `@Transactional` |
| `NotificationService.scanAndGenerateReminders` | **不带**事务 —— 让每条 save() 各自独立事务，避免一条 UNIQUE 冲突污染整批 |
| `AppointmentService.deleteById` | `@Transactional`（v2.2 顺手加，与 cancel/reschedule 保持一致） |

---

## 七、定时任务（v2.2 新增）

- `AppointmentSystemApplication` 加 `@EnableScheduling`
- `ReminderScheduledTask.scheduledRun()` 用 `@Scheduled(cron="0 * * * * *")` 触发
- 实际业务在 `ReminderScheduledTask.runReminders()`，可被测试直接调用
- 通过 `@Value("${app.reminder.scheduler.enabled:true}")` 控制早退
- 测试环境通过 `application-test.properties` 设置 `spring.task.scheduling.enabled=false` + `app.reminder.scheduler.enabled=false` 双保险