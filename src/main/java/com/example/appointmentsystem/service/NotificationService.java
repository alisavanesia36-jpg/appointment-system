package com.example.appointmentsystem.service;

import com.example.appointmentsystem.entity.Appointment;
import com.example.appointmentsystem.entity.AppointmentStatus;
import com.example.appointmentsystem.entity.Notification;
import com.example.appointmentsystem.entity.NotificationType;
import com.example.appointmentsystem.entity.Service;
import com.example.appointmentsystem.entity.Staff;
import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.exception.BusinessException;
import com.example.appointmentsystem.repository.AppointmentRepository;
import com.example.appointmentsystem.repository.NotificationRepository;
import com.example.appointmentsystem.repository.ServiceRepository;
import com.example.appointmentsystem.repository.StaffRepository;
import com.example.appointmentsystem.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * v2.2 第一阶段：站内通知 Service。
 *
 * <p>职责：
 * <ul>
 *   <li>查询 / 标记 / 全部标记已读 —— 仅操作当前登录用户自己的通知</li>
 *   <li>扫描 CONFIRMED 预约生成时间提醒（24h / 1h）—— ReminderScheduledTask 调用</li>
 *   <li>按 appointmentId 删除所有关联通知 —— AppointmentService 联动清理使用</li>
 * </ul>
 *
 * <p>依赖方向：本 Service 仅依赖 Repository，不依赖 AppointmentService，
 * 也不依赖 ReminderScheduledTask。{@code AppointmentService → NotificationService}
 * 单向箭头，无循环依赖风险。
 */
@org.springframework.stereotype.Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** 默认系统时钟；测试可通过 ReflectionTestUtils 注入 Clock.fixed(...) */
    private Clock clock = Clock.systemDefaultZone();

    /** 通知内容时间格式，与前端 formatDateTime 一致 */
    private static final DateTimeFormatter CONTENT_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            ServiceRepository serviceRepository,
            StaffRepository staffRepository,
            AppointmentRepository appointmentRepository) {

        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.staffRepository = staffRepository;
        this.appointmentRepository = appointmentRepository;
    }

    // ==================== 私有工具 ====================

    private User resolveUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    // ==================== 用户侧查询 ====================

    /**
     * 查询当前登录用户的全部通知，按 createdAt 倒序。
     * 不接受任何 userId 参数 —— 永远用 SecurityUtils 拿到的当前用户名。
     */
    public List<Notification> listMine(String username) {
        User currentUser = resolveUser(username);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
    }

    /**
     * 统计当前登录用户的未读通知数量。
     * 首页未读角标使用。
     */
    public long countUnread(String username) {
        User currentUser = resolveUser(username);
        return notificationRepository.countByUserIdAndIsReadFalse(currentUser.getId());
    }

    // ==================== 用户侧标记已读 ====================

    /**
     * 标记单条通知已读。
     *
     * <p>规则：
     * <ul>
     *   <li>通知不存在 → BusinessException("通知不存在") → 400</li>
     *   <li>USER 只能标记自己的通知；非本人 → ResponseStatusException 403</li>
     *   <li>已读通知再次标记 → 幂等返回（不抛错，isRead 仍为 true）</li>
     * </ul>
     */
    @org.springframework.transaction.annotation.Transactional
    public Notification markRead(Long id, String username) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("通知不存在"));

        User currentUser = resolveUser(username);
        if (!notification.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "只能标记自己的通知"
            );
        }

        if (Boolean.FALSE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notificationRepository.save(notification);
        }
        return notification;
    }

    /**
     * 标记当前登录用户全部未读通知为已读，返回被实际修改的行数。
     *
     * <p>实现策略：因当前 NotificationRepository 没有自定义 UPDATE query，
     * 采用 load → iterate → setIsRead(true) → save 模式。仅处理当前用户，
     * 不影响其他用户的通知。
     */
    @org.springframework.transaction.annotation.Transactional
    public int markAllRead(String username) {
        User currentUser = resolveUser(username);
        List<Notification> all = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(currentUser.getId());

        int count = 0;
        for (Notification n : all) {
            if (Boolean.FALSE.equals(n.getIsRead())) {
                n.setIsRead(true);
                notificationRepository.save(n);
                count++;
            }
        }
        return count;
    }

    // ==================== 提醒扫描（ReminderScheduledTask 调用） ====================

    /**
     * 在 [windowStart, windowEnd) 半开区间内扫描 CONFIRMED 预约，
     * 为每个预约生成一条 {@code type} 通知。
     *
     * <p>规则：
     * <ul>
     *   <li>只处理 status == CONFIRMED；PENDING / CANCELLED / COMPLETED 跳过</li>
     *   <li>半开区间：{@code !appointmentTime.isBefore(windowStart) && appointmentTime.isBefore(windowEnd)}</li>
     *   <li>应用层 exists 预检：相同 (relatedAppointmentId, type) 已存在则跳过</li>
     *   <li>DB UNIQUE(relatedAppointmentId, type) 终极兜底；并发触发
     *       DataIntegrityViolationException 时按"已存在"处理，不向上抛 500</li>
     *   <li>Service 或 Staff 不存在的预约 → 跳过 + warn 日志</li>
     * </ul>
     *
     * <p>返回实际生成的通知条数（含跃过已存在的情况）。
     *
     * <p><b>当前实现的最小限制</b>：{@code AppointmentRepository}
     * 没有精确的 {@code findByStatusAndAppointmentTimeBetween} 派生方法，
     * 本方法改用 {@code findByAppointmentTimeBetween(windowStart, windowEnd)}
     * 拉出窗口内所有预约，再在内存里过滤 {@code CONFIRMED}。
     * 数据规模小时足够；规模增大后应在 {@code AppointmentRepository}
     * 新增 {@code List<Appointment> findByStatusAndAppointmentTimeBetween(
     * AppointmentStatus, LocalDateTime, LocalDateTime)} 派生方法以减少内存开销。
     *
     * <p><b>事务策略</b>：本方法不带 {@code @Transactional}，
     * 让 Spring Data 内部 {@code save()} 的默认事务各自独立，
     * 避免一条 {@code DataIntegrityViolationException} 污染整批保存。
     */
    public int scanAndGenerateReminders(
            LocalDateTime now,
            NotificationType type,
            LocalDateTime windowStart,
            LocalDateTime windowEnd) {

        List<Appointment> candidates = appointmentRepository
                .findByAppointmentTimeBetween(windowStart, windowEnd);

        int generated = 0;
        for (Appointment appt : candidates) {
            // 只处理 CONFIRMED；其余状态一律跳过
            if (appt.getStatus() != AppointmentStatus.CONFIRMED) {
                continue;
            }

            // 半开区间 [windowStart, windowEnd)：用 AppointmentRepository 拉出的
            // 数据可能超出 [windowStart, windowEnd) 边界（例如跨午夜的边界场景），
            // 二次校验保险起见。
            LocalDateTime t = appt.getAppointmentTime();
            if (t.isBefore(windowStart) || !t.isBefore(windowEnd)) {
                continue;
            }

            // 应用层幂等预检
            if (notificationRepository.existsByRelatedAppointmentIdAndType(
                    appt.getId(), type)) {
                continue;
            }

            // 读取 Service —— 缺失则跳过该预约（可能 Service 被后台删除）
            Service svc = serviceRepository.findById(appt.getServiceId())
                    .orElse(null);
            if (svc == null) {
                log.warn("scanAndGenerateReminders: appointment {} serviceId={} 未找到，跳过",
                        appt.getId(), appt.getServiceId());
                continue;
            }

            // 读取 Staff —— 缺失则跳过
            Staff staff = staffRepository.findById(appt.getStaffId())
                    .orElse(null);
            if (staff == null) {
                log.warn("scanAndGenerateReminders: appointment {} staffId={} 未找到，跳过",
                        appt.getId(), appt.getStaffId());
                continue;
            }

            Notification notification = new Notification();
            notification.setUserId(appt.getUserId());
            notification.setTitle("预约提醒");
            notification.setRelatedAppointmentId(appt.getId());
            notification.setType(type);
            notification.setIsRead(false);
            notification.setContent(buildContent(svc.getName(), staff.getName(),
                    appt.getAppointmentTime(), type));
            // createdAt 由 Notification.@PrePersist 自动填充，不重复设置

            try {
                notificationRepository.save(notification);
                generated++;
            } catch (DataIntegrityViolationException e) {
                // 并发场景：另一线程刚插入同 (apptId, type)；按"已存在"处理
                log.warn("scanAndGenerateReminders: appointment {} type {} 并发唯一约束命中，已跳过",
                        appt.getId(), type);
            }
        }

        return generated;
    }

    /**
     * 拼接提醒通知的 content。
     * 服务名 / 员工名 / 时间均动态读取，绝不硬编码。
     */
    private String buildContent(
            String serviceName,
            String staffName,
            LocalDateTime appointmentTime,
            NotificationType type) {

        String leadTimeText = (type == NotificationType.APPOINTMENT_24H)
                ? "24 小时"
                : "1 小时";

        return String.format(
                "您的「%s」预约将在 %s 后开始。%n员工：%s%n时间：%s",
                serviceName,
                leadTimeText,
                staffName,
                appointmentTime.format(CONTENT_TIME_FMT)
        );
    }

    // ==================== 联动清理（AppointmentService 调用） ====================

    /**
     * 删除指定预约关联的全部通知。
     * AppointmentService 在 cancel / reschedule / deleteById 成功后调用。
     *
     * <p>不检查用户名 —— 调用方已做完业务校验，这里只是物理清理。
     */
    @org.springframework.transaction.annotation.Transactional
    public int deleteAllByAppointmentId(Long appointmentId) {
        return notificationRepository.deleteByRelatedAppointmentId(appointmentId);
    }

    // ==================== 测试辅助 ====================

    /**
     * 默认使用系统时钟；测试可通过
     * {@code ReflectionTestUtils.setField(service, "clock", Clock.fixed(...))}
     * 注入固定时钟。本字段当前方法列表里暂未直接使用（{@link #scanAndGenerateReminders}
     * 通过 {@code now} 参数接收时间），但保留以与 {@link AppointmentService}
     * 的 {@code clock} 字段风格一致，便于将来增加需要绝对时间的逻辑时直接复用。
     */
}