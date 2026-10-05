# 数据库说明

> 本项目使用 MySQL + Spring Data JPA。`ddl-auto=update`（dev）/ `create-drop`（test）
> 由 Hibernate 自动建表，**不**手写 SQL migration。

---

## 一、数据库清单

| 表名 | 实体 | 主键生成 | 说明 |
|------|------|----------|------|
| `users` | `User` | IDENTITY | 用户表 |
| `services` | `Service` | IDENTITY | 服务表 |
| `staff` | `Staff` | IDENTITY | 员工表 |
| `staff_services` | `StaffServiceMapping` | 复合主键 `staff_service_id` | 员工-服务分配 |
| `appointments` | `Appointment` | IDENTITY | 预约表 |
| `notifications` | `Notification` | IDENTITY | v2.2 站内通知表 |

---

## 二、ER 关系（文字版）

```
users (1) ─────< (N) appointments (N) >────── (1) services
                            │
                            │
                            >────── (1) staff
                            │
                            │
                            (1) >────── (N) notifications (via related_appointment_id)

users (1) ─────< (N) notifications (via user_id)

staff (N) >────── staff_services ────< (N) services
```

---

## 三、字段说明

### 3.1 `users`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK | 自增 |
| `username` | VARCHAR | UNIQUE | 用户名 |
| `password` | VARCHAR | — | BCrypt 加密；`@JsonProperty(WRITE_ONLY)` 不出参 |
| `phone` | VARCHAR | — | 手机号 |
| `role` | VARCHAR(枚举字符串) | — | `USER` / `ADMIN` |

### 3.2 `services`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK | 自增 |
| `name` | VARCHAR | — | 服务名 |
| `duration` | INT | — | 时长（分钟），v2.0 用于计算 available-slots |
| `description` | VARCHAR | — | 服务描述 |

### 3.3 `staff`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK | 自增 |
| `name` | VARCHAR | — | 员工名 |

### 3.4 `staff_services`（员工-服务映射）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `staff_id` | BIGINT | PK(复合) | 员工 id |
| `service_id` | BIGINT | PK(复合) | 服务 id |

复合主键由 `StaffServiceId` 嵌入类封装。员工能预约的服务由这张表决定。

### 3.5 `appointments`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK | 自增 |
| `user_id` | BIGINT | NOT NULL | 预约用户 |
| `service_id` | BIGINT | NOT NULL | 服务 |
| `staff_id` | BIGINT | NOT NULL | 员工 |
| `appointment_time` | DATETIME | NOT NULL | 预约时间（LocalDateTime） |
| `status` | VARCHAR(枚举字符串) | — | `PENDING` / `CONFIRMED` / `CANCELLED` / `COMPLETED` |

注意：Appointment 实体**不**含 `created_at` / `updated_at` / `reminded_*` 字段。v2.2 提醒触发时间完全基于 `appointment_time` 计算。

### 3.6 `notifications`（v2.2 新增）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK | 自增 |
| `user_id` | BIGINT | NOT NULL | 接收人 |
| `title` | VARCHAR(100) | NOT NULL | 标题 |
| `content` | VARCHAR(500) | NOT NULL | 内容（服务名 / 员工名 / 时间） |
| `type` | VARCHAR(50) | NOT NULL | `APPOINTMENT_24H` / `APPOINTMENT_1H`（枚举字符串，非 ordinal） |
| `related_appointment_id` | BIGINT | NOT NULL | 关联预约 |
| `is_read` | BOOLEAN | NOT NULL, 默认 false | 是否已读 |
| `created_at` | DATETIME | NOT NULL | 创建时间（`@PrePersist` 填充） |

**唯一约束：**

```
UNIQUE KEY uk_notifications_appt_type (related_appointment_id, type)
```

由 Hibernate 在 `ddl-auto=update` / `create-drop` 模式自动产出。

**索引（仅主键索引 + UNIQUE 约束）**：

当前规模无需额外索引。如未来通知量增长到 100k+，建议加：

```
INDEX idx_notifications_user_created (user_id, created_at DESC)
INDEX idx_notifications_related_type (related_appointment_id, type)
```

---

## 四、状态机（`Appointment.status`）

```
PENDING  ──── ADMIN confirm ────→  CONFIRMED  ──── ADMIN complete ────→  COMPLETED
   │                                  │
   └──── USER/ADMIN cancel ──→ CANCELLED  ←─── USER/ADMIN cancel ─┘
```

只有 `CONFIRMED` 进入 v2.2 提醒系统。

---

## 五、清理策略

| 触发 | 清理目标 |
|------|----------|
| `AppointmentService.cancel(id)` | `notifications WHERE related_appointment_id = id`（整段物理删除） |
| `AppointmentService.reschedule(id)` | 同上；新时间由下次定时任务按 `appointment_time` 重新生成 |
| `AppointmentService.deleteById(id)`（ADMIN） | 同上 |

**为什么删除包括已读通知？** 取消 / 改期 / 删除后的"1 小时后开始"通知本身是误导信息，保留反而是噪声。真正的审计日志在 `appointments` 表（CANCELLED 行），通知表不充当审计日志。

---

## 六、数据库迁移注意事项

- 项目**不**使用 Flyway / Liquibase
- 任何表结构修改通过修改 Entity + 重启应用（Hibernate 自动 DDL）
- 生产环境推荐 `ddl-auto=validate`，变更通过手动 SQL 脚本执行

---

## 七、命名约定

- 表名：小写 + 下划线（`snake_case`），如 `staff_services`
- 实体字段：camelCase，DB 列显式 `@Column(name = "snake_case")`
- 枚举：`@Enumerated(EnumType.STRING)`，DB 存字符串