package com.example.appointmentsystem.task;

import com.example.appointmentsystem.entity.NotificationType;
import com.example.appointmentsystem.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * v2.2 第一阶段：站内通知定时生成任务。
 *
 * <p>每分钟扫描一次"未来 24h ± 5min"与"未来 1h ± 5min"两个时间窗口内的
 * CONFIRMED 预约，生成对应 APPOINTMENT_24H / APPOINTMENT_1H 通知。
 *
 * <p>设计要点：
 * <ul>
 *   <li>真实业务逻辑在 {@link #runReminders()}，可被测试直接调用</li>
 *   <li>{@link #scheduledRun()} 只是触发器 + 日志</li>
 *   <li>"当前时间"通过 {@link Clock} 注入；测试可通过
 *     {@code ReflectionTestUtils.setField(task, "clock", Clock.fixed(...))} 固定</li>
 *   <li>{@code app.reminder.scheduler.enabled=false} 时直接跳过（早退），便于测试期阻断</li>
 *   <li>幂等：NotificationService 内已做应用层 exists + DB UNIQUE 两层保护</li>
 * </ul>
 */
@Component
public class ReminderScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduledTask.class);

    /** 默认系统时钟；测试可通过 ReflectionTestUtils 注入 Clock.fixed(...) */
    private Clock clock = Clock.systemDefaultZone();

    /**
     * 调度开关，默认开启。
     * 测试环境通过 application-test.properties 设为 false，
     * scheduledRun() 早退，避免测试期真实触发。
     */
    @Value("${app.reminder.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    private final NotificationService notificationService;

    public ReminderScheduledTask(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Spring {@code @Scheduled} 触发器：每分钟一次（cron: "0 * * * * *"）。
     * 仅做调度开关判断 + 调用 {@link #runReminders()} + 简单日志。
     * 不放业务逻辑。
     */
    @Scheduled(cron = "0 * * * * *")
    public void scheduledRun() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            int generated = runReminders();
            if (generated > 0) {
                log.info("ReminderScheduledTask generated {} notifications", generated);
            }
        } catch (Exception e) {
            // 提醒失败绝不能影响主流程
            log.error("ReminderScheduledTask failed", e);
        }
    }

    /**
     * 真正业务入口：扫描两个时间窗口（24h / 1h），生成提醒通知。
     *
     * <p>窗口：±5min 容错
     * <ul>
     *   <li>24h：{@code [appointmentTime - 24h - 5min, appointmentTime - 24h + 5min)}</li>
     *   <li>1h：{@code [appointmentTime - 1h - 5min, appointmentTime - 1h + 5min)}</li>
     * </ul>
     *
     * <p>测试可直接调用本方法（无需等待真实调度）。
     * 返回值：本轮实际生成的通知条数。
     */
    public int runReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        int total = 0;

        // 24h 窗口：要找的预约满足 appointmentTime - 24h ≈ now
        // 也就是 appointmentTime ∈ [now + 24h - 5min, now + 24h + 5min)
        LocalDateTime windowStart24h = now.plusHours(24).minusMinutes(5);
        LocalDateTime windowEnd24h = now.plusHours(24).plusMinutes(5);
        total += notificationService.scanAndGenerateReminders(
                now,
                NotificationType.APPOINTMENT_24H,
                windowStart24h,
                windowEnd24h
        );

        // 1h 窗口：appointmentTime ∈ [now + 1h - 5min, now + 1h + 5min)
        LocalDateTime windowStart1h = now.plusHours(1).minusMinutes(5);
        LocalDateTime windowEnd1h = now.plusHours(1).plusMinutes(5);
        total += notificationService.scanAndGenerateReminders(
                now,
                NotificationType.APPOINTMENT_1H,
                windowStart1h,
                windowEnd1h
        );

        return total;
    }
}