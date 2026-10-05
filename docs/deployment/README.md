# 部署说明

> 本项目规模较小，采用"单机 / 单进程"部署方式：
> - 后端：Spring Boot Jar / War
> - 前端：HBuilderX 发行 H5 静态文件，由 Nginx 反向代理对接后端

---

## 一、依赖版本

| 组件 | 版本 |
|------|------|
| Java | 17+ |
| Maven | 3.9.x（构建用，运行时不需要） |
| MySQL | 8.x（也兼容 5.7+） |
| Nginx | 任意 1.20+ |
| Node.js | 不需要（本项目前端无 npm 第三方依赖） |
| HBuilderX | 5.24（仅前端 H5 发行需要） |

---

## 二、MySQL 建库

```sql
CREATE DATABASE appointment_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

测试库（CI / 本地开发）：

```sql
CREATE DATABASE appointment_system_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 三、后端部署

### 3.1 修改配置

修改 `src/main/resources/application.properties`：

```properties
spring.datasource.url=jdbc:mysql://你的MySQL地址:3306/appointment_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=你的账号
spring.datasource.password=你的密码
spring.jpa.hibernate.ddl-auto=update
server.port=8080

jwt.secret=ThisIsAVeryLongSecretKeyForJwtHmacSha256Signing_0123456789_abcdefghijklmnopqrstuvwxyz
jwt.expiration=3600
```

> ⚠️ 生产环境 `jwt.secret` **务必**替换为高熵随机值，并妥善保管。

### 3.2 打包

```bash
mvn clean package -DskipTests
```

产物：`target/appointment-system-0.0.1-SNAPSHOT.jar`

### 3.3 运行

```bash
java -jar target/appointment-system-0.0.1-SNAPSHOT.jar
# 或指定 profile / 配置
java -jar target/appointment-system-0.0.1-SNAPSHOT.jar --spring.datasource.password=xxx
```

监听 `0.0.0.0:8080`。

### 3.4 systemd 服务（可选）

```ini
# /etc/systemd/system/appointment-system.service
[Unit]
Description=Appointment System Backend
After=mysql.service

[Service]
Type=simple
User=appsvc
ExecStart=/usr/bin/java -jar /opt/appointment-system/appointment-system-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable appointment-system
sudo systemctl start appointment-system
```

---

## 四、前端 H5 部署

### 4.1 发行

1. HBuilderX 5.24 打开 `frontend/` 目录
2. 菜单「发行 → 网站 - PC Web 或手机 H5」
3. 发行完成后产物在 `frontend/unpackage/dist/build/h5/`

### 4.2 Nginx 配置示例

```nginx
server {
    listen 80;
    server_name your.domain.com;

    # H5 静态资源
    location / {
        root /opt/appointment-system/h5;
        try_files $uri $uri/ /index.html;
    }

    # H5 后端代理 —— 保持前缀 /backend-api
    location /backend-api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

> ⚠️ **必须保持 `/backend-api` 前缀**，不要改成 `/api`。
> 原因：uni-app 前端源码目录为 `@/api/*`，如果反向代理前缀也叫 `/api`，会导致
> 前端 JS 资源被错误代理到后端，从而触发白屏 / 接口 404。

### 4.3 测试 H5 与后端连通

```bash
curl http://your.domain.com/backend-api/auth/login \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"username":"admin001","password":"123456"}'
```

返回 `{ "token": "..." }` 即通。

---

## 五、JWT 鉴权说明

- 登录成功返回 JWT
- 前端 `utils/request.js` 自动从 `uni.getStorageSync('token')` 取 token
- 每次请求带 `Authorization: Bearer <token>`
- 401 响应 → 自动清空本地 token + reLaunch 到登录页

生产环境推荐：

- 调整 `jwt.expiration`（如改成 7200 = 2 小时）
- 引入 refresh token（v3.0+ 扩展，当前项目不内置）

---

## 六、CORS / H5 代理说明

> 开发期使用 Vite dev proxy（`/backend-api` → `http://localhost:8080`）。
> 生产期使用 Nginx 同等配置。

**禁止**让前端 H5 直接访问 `http://localhost:8080`，原因：

1. 浏览器跨域 → Spring Security 拦截 → 401
2. 即使允许跨域，token 暴露在更广的 URL 空间，不利于安全
3. 与前端源码目录 `@/api/*` 命名冲突

统一约定：H5 → `/backend-api/...` → Nginx / Vite 代理 → `http://localhost:8080/...`

---

## 七、常见问题

### 7.1 H5 页面白屏

- 检查浏览器 Console：是否有 `navigateTo:fail page ... is not found`
- 确认 `pages.json` 注册了目标页面
- 在 HBuilderX 内重启 H5 即可（HBuilderX 缓存偶发丢失 page route）

### 7.2 登录后立即跳回登录页

- 后端 401 / token 过期
- 清除浏览器 localStorage 后重新登录
- 检查后端 `jwt.secret` 是否一致

### 7.3 `/backend-api/auth/login` 返回 404

- 检查 Nginx `proxy_pass` 末尾是否带斜杠：`/backend-api/` → `http://127.0.0.1:8080/`
- 不要写成 `proxy_pass http://127.0.0.1:8080;`（会把路径保留 `/backend-api/auth/login`，后端路由不识别）

### 7.4 提醒通知一直没生成

- 确认后端 `@EnableScheduling` 已加（v2.2 后默认加）
- 确认 `app.reminder.scheduler.enabled` 没有被设为 `false`（dev 环境默认 `true`）
- 查看后端日志是否有 `ReminderScheduledTask generated N notifications` 或相关 warn
- 检查 appointment.status 是否为 `CONFIRMED`（PENDING 不会生成）
- 检查 appointment.appointment_time 是否在当前时间的 [24h±5min, 1h±5min] 窗口内

### 7.5 重复通知

- 不会发生。数据库 `UNIQUE(related_appointment_id, type)` 唯一约束 + 应用层 exists 预检 + catch DataIntegrityViolationException 三层保护
- 如果遇到重复，99% 是手动 `INSERT` 绕过了应用层（请改用应用接口）

---

## 八、监控 / 日志

当前项目不内置 Prometheus / Actuator。如生产环境需要：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

即可暴露健康检查、JVM 指标。本项目刻意保持精简，未默认引入。

---

## 九、升级路径

| 未来扩展 | 难度 |
|----------|------|
| 邮件 / 短信通知 | 低，新增 NotificationType + NotificationSender |
| WebSocket 实时推送 | 中，新增 WebSocketConfig + 复用 NotificationService |
| 多管理员权限分级 | 中，新增 Role 扩展或 Authority 表 |
| 分布式部署 | 高，需要 Redis / 消息队列，当前不推荐 |

按"先上线、再演进"原则，建议先用当前单进程版上线，跑通业务后再决定是否引入中间件。