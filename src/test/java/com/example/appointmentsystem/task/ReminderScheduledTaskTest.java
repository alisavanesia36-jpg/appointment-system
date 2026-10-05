package com.example.appointmentsystem.task;

import com.example.appointmentsystem.entity.Appointment;
import com.example.appointmentsystem.entity.AppointmentStatus;
import com.example.appointmentsystem.entity.Notification;
import com.example.appointmentsystem.entity.NotificationType;
import com.example.appointmentsystem.entity.Role;
import com.example.appointmentsystem.entity.Service;
import com.example.appointmentsystem.entity.Staff;
import com.example.appointmentsystem.entity.StaffServiceId;
import com.example.appointmentsystem.entity.StaffServiceMapping;
import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.repository.AppointmentRepository;
import com.example.appointmentsystem.repository.NotificationRepository;
import com.example.appointmentsystem.repository.ServiceRepository;
import com.example.appointmentsystem.repository.StaffRepository;
import com.example.appointmentsystem.repository.StaffServiceMappingRepository;
import com.example.appointmentsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v2.2 第一阶段：ReminderScheduledTask 行为测试。
 *
 * <p>覆盖用户列出的 12 个用例（8-19）+ 1 个幂等测试（24）：
 * <ol start="8">
 *   <li>CONFIRMED → 24h reminder</li>
 *   <li>CONFIRMED → 1h reminder</li>
 *   <li>PENDING → 不生成</li>
 *   <li>CANCELLED → 不生成</li>
 *   <li>COMPLETED → 不生成</li>
 *   <li>24h 重复执行不重复</li>
 *   <li>1h 重复执行不重复</li>
 *   <li>已过期预约不生成</li>
 *   <li>不同用户通知互不影响</li>
 *   <li>同一预约 24h + 1h 可以分别存在</li>
 *   <li>固定 Clock 窗口外不生成</li>
 *   <li>固定 Clock 窗口内生成</li>
 *   <li>DB UNIQUE 防止重复</li>
 * </ol>
 *
 * <p>时间通过 {@code ReflectionTestUtils.setField(task, "clock", Clock.fixed(...))}
 * 注入固定时钟，不依赖运行机器的真实时间。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReminderScheduledTaskTest {

    @Autowired
    private ReminderScheduledTask reminderTask;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private StaffServiceMappingRepository staffServiceMappingRepository;

    private static final LocalDate TARGET_DATE = LocalDate.of(2030, 1, 1);
    private static final ZoneId Z = ZoneId.of("Asia/Shanghai");

    @BeforeEach
    void resetClock() {
        ReflectionTestUtils.setField(
                reminderTask, "clock", Clock.systemDefaultZone());
    }

    private void setFixedClockTo(LocalDateTime time) {
        Instant instant = time.atZone(Z).toInstant();
        ReflectionTestUtils.setField(
                reminderTask, "clock", Clock.fixed(instant, Z));
    }

    private LocalDateTime atTime(int hour, int minute) {
        return TARGET_DATE.atTime(hour, minute);
    }

    // ==================== 辅助：构造测试数据 ====================

    private User createUser(String prefix, Role role) {
        User u = new User();
        // username 必须与 @WithMockUser(username=...) 或 SecurityUtils.getCurrentUsername() 一致
        // 每个测试方法使用不同的 prefix 避免冲突
        u.setUsername(prefix);
        u.setPassword("x");
        u.setPhone("138" + System.nanoTime());
        u.setRole(role);
        return userRepository.save(u);
    }

    private Service createService(int duration) {
        Service s = new Service();
        s.setName("reminder_svc_" + System.nanoTime());
        s.setDuration(duration);
        s.setPrice(100.0);
        return serviceRepository.save(s);
    }

    private Staff createStaff() {
        Staff s = new Staff();
        s.setName("reminder_staff_" + System.nanoTime());
        s.setPhone("13900000000");
        return staffRepository.save(s);
    }

    private void createMapping(Long staffId, Long serviceId) {
        staffServiceMappingRepository.save(
                new StaffServiceMapping(new StaffServiceId(staffId, serviceId)));
    }

    private Appointment seedAppointment(Long userId, Long serviceId, Long staffId,
                                        LocalDateTime time, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setUserId(userId);
        a.setServiceId(serviceId);
        a.setStaffId(staffId);
        a.setAppointmentTime(time);
        a.setStatus(status);
        return appointmentRepository.save(a);
    }

    // ==================== 用例 8: CONFIRMED → 24h reminder ====================

    @Test
    void confirmedAppointment_generates24hReminder() {
        User alice = createUser("alice8", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.CONFIRMED);

        // 当前时刻 = 14:00 - 24h = 前一天 14:00，正好命中 24h 窗口
        setFixedClockTo(atTime(14, 0).minusHours(24));

        int generated = reminderTask.runReminders();

        assertEquals(1, generated);
        Notification n = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(alice.getId()).get(0);
        assertEquals(NotificationType.APPOINTMENT_24H, n.getType());
        assertEquals(appt.getId(), n.getRelatedAppointmentId());
        assertEquals(alice.getId(), n.getUserId());
        assertEquals("预约提醒", n.getTitle());
        assertNotNull(n.getContent());
        assertTrue(n.getContent().contains(svc.getName()));
        assertTrue(n.getContent().contains(staff.getName()));
        assertTrue(n.getContent().contains("24 小时"));
    }

    // ==================== 用例 9: CONFIRMED → 1h reminder ====================

    @Test
    void confirmedAppointment_generates1hReminder() {
        User alice = createUser("alice9", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.CONFIRMED);

        // 当前时刻 = 14:00 - 1h = 13:00，正好命中 1h 窗口
        setFixedClockTo(atTime(14, 0).minusHours(1));

        int generated = reminderTask.runReminders();

        assertEquals(1, generated);
        Notification n = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(alice.getId()).get(0);
        assertEquals(NotificationType.APPOINTMENT_1H, n.getType());
        assertEquals(appt.getId(), n.getRelatedAppointmentId());
        assertTrue(n.getContent().contains("1 小时"));
    }

    // ==================== 用例 10: PENDING → 不生成 ====================

    @Test
    void pendingAppointment_generatesNoReminder() {
        User alice = createUser("alice10", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(14, 0).minusHours(24));

        int generated = reminderTask.runReminders();

        assertEquals(0, generated);
        assertEquals(0, notificationRepository.countByUserIdAndIsReadFalse(alice.getId()));
    }

    // ==================== 用例 11: CANCELLED → 不生成 ====================

    @Test
    void cancelledAppointment_generatesNoReminder() {
        User alice = createUser("alice11", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.CANCELLED);

        setFixedClockTo(atTime(14, 0).minusHours(24));

        int generated = reminderTask.runReminders();

        assertEquals(0, generated);
        assertEquals(0, notificationRepository.countByUserIdAndIsReadFalse(alice.getId()));
    }

    // ==================== 用例 12: COMPLETED → 不生成 ====================

    @Test
    void completedAppointment_generatesNoReminder() {
        User alice = createUser("alice12", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.COMPLETED);

        setFixedClockTo(atTime(14, 0).minusHours(24));

        int generated = reminderTask.runReminders();

        assertEquals(0, generated);
        assertEquals(0, notificationRepository.countByUserIdAndIsReadFalse(alice.getId()));
    }

    // ==================== 用例 13: 24h 重复执行不重复 ====================

    @Test
    void repeatRun_doesNotDuplicate24h() {
        User alice = createUser("alice13", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.CONFIRMED);

        setFixedClockTo(atTime(14, 0).minusHours(24));

        int firstRun = reminderTask.runReminders();
        int secondRun = reminderTask.runReminders();
        int thirdRun = reminderTask.runReminders();

        assertEquals(1, firstRun);
        assertEquals(0, secondRun);
        assertEquals(0, thirdRun);

        // DB 中只有一条 APPOINTMENT_24H
        List<Notification> all = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(alice.getId());
        assertEquals(1, all.size());
        assertEquals(NotificationType.APPOINTMENT_24H, all.get(0).getType());
    }

    // ==================== 用例 14: 1h 重复执行不重复 ====================

    @Test
    void repeatRun_doesNotDuplicate1h() {
        User alice = createUser("alice14", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.CONFIRMED);

        setFixedClockTo(atTime(14, 0).minusHours(1));

        int firstRun = reminderTask.runReminders();
        int secondRun = reminderTask.runReminders();

        assertEquals(1, firstRun);
        assertEquals(0, secondRun);
    }

    // ==================== 用例 15: 已过期预约不生成 ====================

    @Test
    void expiredAppointment_generatesNoReminder() {
        User alice = createUser("alice15", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        // 预约在当前时钟"之前"（已过期）
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(8, 0), AppointmentStatus.CONFIRMED);

        // 时钟比预约还早——此时 24h 窗口是 [-16h, -15:55) 之类的过去时段
        // 但 appointment 状态正常 CONFIRMED，所以走完半开区间会被跳过
        // （appointmentTime isBefore windowStart）。
        // 真正业务里这种"过期"预约应通过状态过滤；这里额外断言：用同时刻测试，
        // 把 appointmentTime 设成 windowStart 之前 1 秒，确认 isBefore 触发
        setFixedClockTo(atTime(10, 0));

        // 当前窗口是 [10:00-24h-5min, 10:00-24h+5min) = [前天10:05, 前天10:15)
        // 预约 8:00 不在窗口内
        int generated = reminderTask.runReminders();
        assertEquals(0, generated);
    }

    // ==================== 用例 16: 不同用户通知互不影响 ====================

    @Test
    void differentUsers_isolated() {
        User alice = createUser("alice16", Role.USER);
        User bob = createUser("bob16", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.CONFIRMED);
        seedAppointment(bob.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.CONFIRMED);

        setFixedClockTo(atTime(14, 0).minusHours(24));

        int generated = reminderTask.runReminders();

        assertEquals(2, generated);
        assertEquals(1, notificationRepository.countByUserIdAndIsReadFalse(alice.getId()));
        assertEquals(1, notificationRepository.countByUserIdAndIsReadFalse(bob.getId()));
    }

    // ==================== 用例 17: 同一预约 24h + 1h 可分别存在 ====================

    @Test
    void sameAppointment_canHaveBothReminderTypes() {
        User alice = createUser("alice17", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.CONFIRMED);

        // 第一次扫描：24h 窗口命中
        setFixedClockTo(atTime(14, 0).minusHours(24));
        int firstRun = reminderTask.runReminders();
        assertEquals(1, firstRun);

        // 第二次扫描：1h 窗口命中（同一预约，不同 type）
        setFixedClockTo(atTime(14, 0).minusHours(1));
        int secondRun = reminderTask.runReminders();
        assertEquals(1, secondRun);

        // DB 中两条通知：一条 24H、一条 1H
        List<Notification> all = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(alice.getId());
        assertEquals(2, all.size());
        boolean has24h = all.stream().anyMatch(n -> n.getType() == NotificationType.APPOINTMENT_24H);
        boolean has1h = all.stream().anyMatch(n -> n.getType() == NotificationType.APPOINTMENT_1H);
        assertTrue(has24h);
        assertTrue(has1h);
    }

    // ==================== 用例 18: 固定 Clock 窗口外不生成 ====================

    @Test
    void outsideWindow_noGeneration() {
        User alice = createUser("alice18", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.CONFIRMED);

        // 时钟比 24h 窗口起点早 6 分钟：不在窗口内
        setFixedClockTo(atTime(14, 0).minusHours(24).minusMinutes(6));

        int generated = reminderTask.runReminders();
        assertEquals(0, generated);
    }

    // ==================== 用例 19: 固定 Clock 窗口内生成 ====================

    @Test
    void insideWindow_generates() {
        User alice = createUser("alice19", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.CONFIRMED);

        // 时钟正好处于 24h 窗口中心偏右
        setFixedClockTo(atTime(14, 0).minusHours(24).plusMinutes(3));

        int generated = reminderTask.runReminders();
        assertEquals(1, generated);
    }

    // ==================== 用例 24: DB UNIQUE 防重复 ====================

    @Test
    void dbUniqueConstraint_preventsDuplicate() {
        User alice = createUser("alice24", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(14, 0), AppointmentStatus.CONFIRMED);

        // 直接 repository.save() 两条相同 (relatedAppointmentId, type)
        Notification first = new Notification();
        first.setUserId(alice.getId());
        first.setTitle("预约提醒");
        first.setContent("first");
        first.setRelatedAppointmentId(appt.getId());
        first.setType(NotificationType.APPOINTMENT_24H);
        first.setIsRead(false);
        first.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(first);

        Notification second = new Notification();
        second.setUserId(alice.getId());
        second.setTitle("预约提醒");
        second.setContent("second");
        second.setRelatedAppointmentId(appt.getId());
        second.setType(NotificationType.APPOINTMENT_24H);
        second.setIsRead(false);
        second.setCreatedAt(LocalDateTime.now());

        // UNIQUE 约束触发：抛 DataIntegrityViolationException
        assertThrows(DataIntegrityViolationException.class, () -> {
            notificationRepository.saveAndFlush(second);
        });
    }
}