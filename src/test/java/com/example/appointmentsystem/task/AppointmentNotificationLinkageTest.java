package com.example.appointmentsystem.task;

import com.example.appointmentsystem.entity.Appointment;
import com.example.appointmentsystem.entity.AppointmentStatus;
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
import com.example.appointmentsystem.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v2.2 第一阶段：AppointmentService 联动清理通知的测试。
 *
 * <p>覆盖用户列出的 4 个用例（20-23）：
 * <ol start="20">
 *   <li>已生成提醒后 reschedule → 旧提醒清理</li>
 *   <li>reschedule 到新时间 → 新时间可以重新产生提醒</li>
 *   <li>已生成提醒后 cancel → 通知清理</li>
 *   <li>已生成提醒后 ADMIN delete → 通知清理</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppointmentNotificationLinkageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private ReminderScheduledTask reminderTask;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private StaffServiceMappingRepository staffServiceMappingRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private static final LocalDate TARGET_DATE = LocalDate.of(2030, 1, 1);
    private static final ZoneId Z = ZoneId.of("Asia/Shanghai");

    private User createUser(String prefix, Role role) {
        User u = new User();
        // username 必须与 @WithMockUser(username=...) 一致
        // 每个测试方法使用不同的 prefix 避免冲突
        u.setUsername(prefix);
        u.setPassword("x");
        u.setPhone("138" + System.nanoTime());
        u.setRole(role);
        return userRepository.save(u);
    }

    private Service createService(int duration) {
        Service s = new Service();
        s.setName("linkage_svc_" + System.nanoTime());
        s.setDuration(duration);
        s.setPrice(100.0);
        return serviceRepository.save(s);
    }

    private Staff createStaff() {
        Staff s = new Staff();
        s.setName("linkage_staff_" + System.nanoTime());
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

    private void setFixedClockTo(LocalDateTime time) {
        ReflectionTestUtils.setField(
                reminderTask, "clock", Clock.fixed(time.atZone(Z).toInstant(), Z));
    }

    private String rescheduleBody(LocalDateTime newTime) {
        return "{\"appointmentTime\":\""
                + newTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\"}";
    }

    private void generate24hReminderForAppointment(Appointment appt, User alice) {
        // 时钟 = appointmentTime - 24h ± 几 min，命中 24h 窗口
        setFixedClockTo(appt.getAppointmentTime().minusHours(24));
        int generated = reminderTask.runReminders();
        // 至少一条 APPOINTMENT_24H 生成（可能还有别的并发预约，但这个测试只关心 alice 的预约）
        assertEquals(true, generated >= 1);

        // 验证 alice 收到一条 APPOINTMENT_24H 关联到该预约
        List<com.example.appointmentsystem.entity.Notification> before = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(alice.getId());
        boolean has = before.stream().anyMatch(
                n -> n.getRelatedAppointmentId().equals(appt.getId()));
        assertEquals(true, has, "24h 提醒应为该预约生成");
    }

    // ==================== 用例 20: reschedule 后旧提醒清理 ====================

    @Test
    @WithMockUser(username = "alice20", roles = "USER")
    void reschedule_clearsOldReminders() throws Exception {
        User alice = createUser("alice20", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                TARGET_DATE.atTime(14, 0), AppointmentStatus.CONFIRMED);

        // 1. 先生成 24h 提醒
        generate24hReminderForAppointment(appt, alice);

        // 2. 用户改期到 2030-01-02 14:00（避开原窗口）
        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(LocalDateTime.of(2030, 1, 2, 14, 0))))
                .andExpect(status().isOk());

        // 3. 旧提醒已清空
        List<com.example.appointmentsystem.entity.Notification> after =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(alice.getId());
        long countForAppt = after.stream()
                .filter(n -> n.getRelatedAppointmentId().equals(appt.getId()))
                .count();
        assertEquals(0, countForAppt, "改期后旧提醒应被清空");
    }

    // ==================== 用例 21: reschedule 后新时间可重新生成 ====================

    @Test
    @WithMockUser(username = "alice21", roles = "USER")
    void reschedule_newTimeCanRegenerate() throws Exception {
        User alice = createUser("alice21", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());

        // 初始时间 2030-01-01 14:00，先生成 24h 提醒
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                TARGET_DATE.atTime(14, 0), AppointmentStatus.CONFIRMED);
        generate24hReminderForAppointment(appt, alice);

        // 改期到 2030-01-03 14:00
        LocalDateTime newTime = LocalDateTime.of(2030, 1, 3, 14, 0);
        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(newTime)))
                .andExpect(status().isOk());

        // 把时钟推到新时间 - 24h
        setFixedClockTo(newTime.minusHours(24));
        reminderTask.runReminders();

        // 现在应该有 APPOINTMENT_24H 关联到该 appointment
        List<com.example.appointmentsystem.entity.Notification> after =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(alice.getId());
        boolean has = after.stream().anyMatch(n ->
                n.getRelatedAppointmentId().equals(appt.getId())
                        && n.getType() == com.example.appointmentsystem.entity.NotificationType.APPOINTMENT_24H);
        assertEquals(true, has, "改期后新时间应能重新生成 24h 提醒");
    }

    // ==================== 用例 22: cancel 后通知清理 ====================

    @Test
    @WithMockUser(username = "alice22", roles = "USER")
    void cancel_clearsReminders() throws Exception {
        User alice = createUser("alice22", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                TARGET_DATE.atTime(14, 0), AppointmentStatus.CONFIRMED);

        // 1. 先生成 24h 提醒
        generate24hReminderForAppointment(appt, alice);

        // 2. 取消
        mockMvc.perform(put("/appointments/" + appt.getId() + "/cancel"))
                .andExpect(status().isOk());

        // 3. 通知被清空
        List<com.example.appointmentsystem.entity.Notification> after =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(alice.getId());
        long countForAppt = after.stream()
                .filter(n -> n.getRelatedAppointmentId().equals(appt.getId()))
                .count();
        assertEquals(0, countForAppt, "取消后通知应被清空");
    }

    // ==================== 用例 23: ADMIN delete 后通知清理 ====================

    @Test
    @WithMockUser(username = "admin23", roles = "ADMIN")
    void adminDelete_clearsReminders() throws Exception {
        User alice = createUser("alice23", Role.USER);
        // 创建 ADMIN 账号，与 @WithMockUser username 一致
        User admin = createUser("admin23", Role.ADMIN);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                TARGET_DATE.atTime(14, 0), AppointmentStatus.CONFIRMED);

        // 1. 先生成 24h 提醒
        generate24hReminderForAppointment(appt, alice);

        // 2. ADMIN 删除预约
        mockMvc.perform(delete("/appointments/" + appt.getId()))
                .andExpect(status().isOk());

        // 3. 通知被清空
        List<com.example.appointmentsystem.entity.Notification> after =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(alice.getId());
        long countForAppt = after.stream()
                .filter(n -> n.getRelatedAppointmentId().equals(appt.getId()))
                .count();
        assertEquals(0, countForAppt, "ADMIN 删除预约后通知应被清空");

        // 4. 删除后再触发 runReminders，不应产生新通知
        setFixedClockTo(appt.getAppointmentTime().minusHours(24));
        reminderTask.runReminders();

        List<com.example.appointmentsystem.entity.Notification> after2 =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(alice.getId());
        long countForAppt2 = after2.stream()
                .filter(n -> n.getRelatedAppointmentId().equals(appt.getId()))
                .count();
        assertEquals(0, countForAppt2, "ADMIN 删除后不应产生新通知");
    }
}