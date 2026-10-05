package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.Appointment;
import com.example.appointmentsystem.entity.AppointmentStatus;
import com.example.appointmentsystem.entity.Role;
import com.example.appointmentsystem.entity.Service;
import com.example.appointmentsystem.entity.Staff;
import com.example.appointmentsystem.entity.StaffServiceId;
import com.example.appointmentsystem.entity.StaffServiceMapping;
import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.repository.AppointmentRepository;
import com.example.appointmentsystem.repository.ServiceRepository;
import com.example.appointmentsystem.repository.StaffRepository;
import com.example.appointmentsystem.repository.StaffServiceMappingRepository;
import com.example.appointmentsystem.repository.UserRepository;
import com.example.appointmentsystem.service.AppointmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v2.1 第一阶段：PUT /appointments/{id}/reschedule 端点测试
 *
 * 覆盖用户列出的 20 个用例：
 * 1. USER 修改自己的 PENDING 成功
 * 2. USER 修改自己的 CONFIRMED 成功
 * 3. USER 修改别人预约失败
 * 4. USER 修改 CANCELLED 失败
 * 5. USER 修改 COMPLETED 失败
 * 6. ADMIN 修改他人 PENDING 成功
 * 7. 修改后时间冲突失败
 * 8. 修改后超过 18:00 失败
 * 9. 修改后早于当前时间失败
 * 10. 员工服务关系异常失败
 * 11. 相邻时间修改成功
 * 12. available-slots + excludeAppointmentId 返回原时段
 * 13. excludeAppointmentId 对其它预约无效
 * 14. excludeAppointmentId 不存在返回业务错误
 * 15. 未登录 reschedule 返回 401
 * 16. reschedule 不允许修改 serviceId
 * 17. reschedule 不允许修改 staffId
 * 18. CANCELLED 预约仍然不影响 available-slots
 * 19. 原有 v2.0 available-slots 测试全部继续通过
 * 20. 原有全部测试全部继续通过
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppointmentRescheduleTest {

    @Autowired
    private MockMvc mockMvc;

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
    private AppointmentService appointmentService;

    // 固定未来日期 2030-01-01，确保可预测
    private static final LocalDate TARGET_DATE = LocalDate.of(2030, 1, 1);
    private static final String TARGET_DATE_STR = "2030-01-01";
    private static final ZoneId Z = ZoneId.of("Asia/Shanghai");

    // 每个测试方法前重置 Clock
    @BeforeEach
    void resetClock() {
        ReflectionTestUtils.setField(
                appointmentService, "clock", Clock.systemDefaultZone());
    }

    private void setFixedClockTo(LocalDateTime time) {
        Instant instant = time.atZone(Z).toInstant();
        ReflectionTestUtils.setField(
                appointmentService, "clock", Clock.fixed(instant, Z));
    }

    private LocalDateTime atTime(int hour, int minute) {
        return TARGET_DATE.atTime(hour, minute);
    }

    // ==================== 辅助：构造测试数据 ====================

    /**
     * 创建指定 username 的用户。
     *
     * 注意：Spring Security 的 @WithMockUser(username = "X") 会让 SecurityUtils.getCurrentUsername()
     * 返回 "X"。Service 层的 resolveUser(username) 通过 userRepository.findByUsername("X") 找用户。
     * 因此本测试 createUser 的 username 必须与 @WithMockUser 的 username 一致。
     * username 在 users 表是 unique 的，所以每个测试的测试方法内必须用不同的 username。
     */
    private User createUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("x");
        u.setPhone("138" + System.nanoTime());
        u.setRole(role);
        return userRepository.save(u);
    }

    private Service createService(int duration) {
        Service s = new Service();
        s.setName("reschedule_svc_" + System.nanoTime());
        s.setDuration(duration);
        s.setPrice(100.0);
        return serviceRepository.save(s);
    }

    private Staff createStaff() {
        Staff s = new Staff();
        s.setName("reschedule_staff_" + System.nanoTime());
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

    /**
     * 注意：这里的 username 仅作为 Spring Security 上下文标记，
     * Service 层通过 SecurityUtils.getCurrentUsername() 拿到的就是它；
     * @WithMockUser(username = "alice") 默认 role = USER，所以调 /users/me 改回的 userId 就是 alice.getId()。
     *
     * 但本测试类不依赖 alice 的 id；改期场景下 userId 是从数据库读出的，
     * 所以"别人的预约"是另一个 User。
     */
    private String rescheduleBody(LocalDateTime newTime) {
        return "{\"appointmentTime\":\""
                + newTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "\"}";
    }

    // ==================== 用例 1: USER 改 PENDING 成功 ====================

    @Test
    @WithMockUser(username = "alice1", roles = "USER")
    void reschedule_userOwnPending_succeeds() throws Exception {
        User alice = createUser("alice1", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(14, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appt.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                // 关键：serviceId / staffId 没变
                .andExpect(jsonPath("$.serviceId").value(svc.getId()))
                .andExpect(jsonPath("$.staffId").value(staff.getId()))
                .andExpect(jsonPath("$.appointmentTime")
                        .value("2030-01-01T14:00:00"));
    }

    // ==================== 用例 2: USER 改 CONFIRMED 成功 ====================

    @Test
    @WithMockUser(username = "alice2", roles = "USER")
    void reschedule_userOwnConfirmed_succeeds() throws Exception {
        User alice = createUser("alice2", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.CONFIRMED);

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(15, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.appointmentTime")
                        .value("2030-01-01T15:00:00"));
    }

    // ==================== 用例 3: USER 改别人失败 ====================

    @Test
    @WithMockUser(username = "currentuser3", roles = "USER")
    void reschedule_otherUsersAppointment_returns403() throws Exception {
        User me = createUser("currentuser3", Role.USER);
        User other = createUser("otheruser3", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        // otheruser 的预约
        Appointment othersAppt = seedAppointment(
                other.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        mockMvc.perform(put("/appointments/" + othersAppt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(14, 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("只能修改自己的预约"));
    }

    // ==================== 用例 4: USER 改 CANCELLED 失败 ====================

    @Test
    @WithMockUser(username = "alice4", roles = "USER")
    void reschedule_cancelledAppointment_returns400() throws Exception {
        User alice = createUser("alice4", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.CANCELLED);

        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(14, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("该预约状态不允许修改"));
    }

    // ==================== 用例 5: USER 改 COMPLETED 失败 ====================

    @Test
    @WithMockUser(username = "alice5", roles = "USER")
    void reschedule_completedAppointment_returns400() throws Exception {
        User alice = createUser("alice5", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.COMPLETED);

        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(14, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("该预约状态不允许修改"));
    }

    // ==================== 用例 6: ADMIN 改他人 PENDING 成功 ====================

    @Test
    @WithMockUser(username = "admin6", roles = "ADMIN")
    void reschedule_adminModifiesOthersPending_succeeds() throws Exception {
        User alice = createUser("alice6", Role.USER);
        User admin = createUser("admin6", Role.ADMIN);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(16, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(alice.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.appointmentTime")
                        .value("2030-01-01T16:00:00"));
    }

    // ==================== 用例 7: 修改后冲突 ====================

    @Test
    @WithMockUser(username = "alice7", roles = "USER")
    void reschedule_toConflictingTime_returns400() throws Exception {
        User alice = createUser("alice7", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        // 已有 10:00-11:00 占用
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(13, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(10, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("这个员工在这个时间段已经有预约了"));
    }

    // ==================== 用例 8: 修改后越过 18:00 ====================

    @Test
    @WithMockUser(username = "alice8", roles = "USER")
    void reschedule_toTimeBeyondBusinessHours_returns400() throws Exception {
        User alice = createUser("alice8", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        // 17:30 + 60min = 18:30 超出 18:00
        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(17, 30))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("预约时间必须在营业时间09:00-18:00内"));
    }

    // ==================== 用例 9: 修改后早于当前时间 ====================

    @Test
    @WithMockUser(username = "alice9", roles = "USER")
    void reschedule_toPastTime_returns400() throws Exception {
        User alice = createUser("alice9", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(15, 0), AppointmentStatus.PENDING);

        // 固定当前时间到 14:00，让 10:00 已过期
        setFixedClockTo(atTime(14, 0));

        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(10, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("预约时间不能早于当前时间"));
    }

    // ==================== 用例 10: 员工服务关系异常 ====================

    @Test
    @WithMockUser(username = "alice10", roles = "USER")
    void reschedule_staffServiceMappingRemoved_returns400() throws Exception {
        User alice = createUser("alice10", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        // 删除员工-服务关系（模拟管理员在改期期间移除）
        staffServiceMappingRepository.deleteById(
                new StaffServiceId(staff.getId(), svc.getId()));

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(14, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("这个员工不会做这个服务"));
    }

    // ==================== 用例 11: 相邻时间成功 ====================

    @Test
    @WithMockUser(username = "alice11", roles = "USER")
    void reschedule_toAdjacentTime_succeeds() throws Exception {
        User alice = createUser("alice11", Role.USER);
        Service svc = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc.getId());
        // 已有 09:00-10:00
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(9, 0), AppointmentStatus.PENDING);
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(13, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        // 改期到 10:00（与 09:00-10:00 相邻）
        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(10, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentTime")
                        .value("2030-01-01T10:00:00"));
    }

    // ==================== 用例 12: available-slots + excludeAppointmentId 返回原时段 ====================

    @Test
    @WithMockUser(username = "alice12", roles = "USER")
    void availableSlots_withExcludeId_returnsOriginalTime() throws Exception {
        User alice = createUser("alice12", Role.USER);
        Staff staff = createStaff();
        Service svc = createService(60);
        createMapping(staff.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR)
                        .param("excludeAppointmentId",
                                String.valueOf(appt.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$", hasItem("10:00")));
    }

    // ==================== 用例 13: excludeAppointmentId 对其它预约无效 ====================

    @Test
    @WithMockUser(username = "alice13", roles = "USER")
    void availableSlots_excludeIdForDifferentStaff_returns400() throws Exception {
        User alice = createUser("alice13", Role.USER);
        Staff staffA = createStaff();
        Staff staffB = createStaff();
        Service svc = createService(60);
        createMapping(staffA.getId(), svc.getId());
        createMapping(staffB.getId(), svc.getId());
        // 预约在 staffA 上
        Appointment apptA = seedAppointment(
                alice.getId(), svc.getId(), staffA.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        // 但调 available-slots 时传 staffB：excludeId 的 staffId 不匹配 → 400
        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staffB.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR)
                        .param("excludeAppointmentId",
                                String.valueOf(apptA.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("预约不存在或不匹配"));
    }

    // ==================== 用例 14: excludeAppointmentId 不存在返回业务错误 ====================

    @Test
    @WithMockUser(username = "alice14", roles = "USER")
    void availableSlots_excludeIdNotFound_returns400() throws Exception {
        Staff staff = createStaff();
        Service svc = createService(60);
        createMapping(staff.getId(), svc.getId());

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR)
                        .param("excludeAppointmentId", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("预约不存在或不匹配"));
    }

    // ==================== 用例 15: 未登录 reschedule 返回 401 ====================

    @Test
    @WithAnonymousUser
    void reschedule_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/appointments/1/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody(atTime(14, 0))))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 用例 16: reschedule 不允许修改 serviceId ====================

    @Test
    @WithMockUser(username = "alice16", roles = "USER")
    void reschedule_serviceIdIgnored_keepsOriginal() throws Exception {
        User alice = createUser("alice16", Role.USER);
        Service svc1 = createService(60);
        Service svc2 = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), svc1.getId());
        createMapping(staff.getId(), svc2.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc1.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        // 试图在 body 里塞 serviceId=svc2 → 应被忽略，serviceId 仍是 svc1
        String body = "{"
                + "\"appointmentTime\":\"2030-01-01T14:00:00\","
                + "\"serviceId\":" + svc2.getId()
                + "}";

        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(svc1.getId()))
                .andExpect(jsonPath("$.appointmentTime")
                        .value("2030-01-01T14:00:00"));
    }

    // ==================== 用例 17: reschedule 不允许修改 staffId ====================

    @Test
    @WithMockUser(username = "alice17", roles = "USER")
    void reschedule_staffIdIgnored_keepsOriginal() throws Exception {
        User alice = createUser("alice17", Role.USER);
        Service svc = createService(60);
        Staff staff1 = createStaff();
        Staff staff2 = createStaff();
        createMapping(staff1.getId(), svc.getId());
        createMapping(staff2.getId(), svc.getId());
        Appointment appt = seedAppointment(
                alice.getId(), svc.getId(), staff1.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        // 试图在 body 里塞 staffId=staff2 → 应被忽略，staffId 仍是 staff1
        String body = "{"
                + "\"appointmentTime\":\"2030-01-01T14:00:00\","
                + "\"staffId\":" + staff2.getId()
                + "}";

        mockMvc.perform(put("/appointments/" + appt.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffId").value(staff1.getId()));
    }

    // ==================== 用例 18: CANCELLED 预约不影响 available-slots ====================

    @Test
    @WithMockUser(username = "alice18", roles = "USER")
    void availableSlots_cancelledDoesNotBlockAndExcludeIgnoredForIt() throws Exception {
        User alice = createUser("alice18", Role.USER);
        Staff staff = createStaff();
        Service svc = createService(60);
        createMapping(staff.getId(), svc.getId());
        // 一条 CANCELLED 预约（不影响 busy）+ 一条 PENDING 预约
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(9, 0), AppointmentStatus.CANCELLED);
        seedAppointment(alice.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        // 不传 excludeId：09:00 应可选（CANCELLED 不阻塞），10:00 不可选（PENDING 阻塞）
        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$", hasItem("09:00")))
                .andExpect(jsonPath("$", not(hasItem("10:00"))));
    }
}