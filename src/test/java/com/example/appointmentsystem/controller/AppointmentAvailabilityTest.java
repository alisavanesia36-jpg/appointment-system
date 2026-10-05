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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v2.0 第一阶段：GET /appointments/available-slots 端点测试
 *
 * 覆盖：
 * - 员工不存在 / 服务不存在 / 员工不支持服务 → 400 + 业务 message
 * - 当天无预约 duration=60 / 缺失期 → 标准 9 个 slot
 * - PENDING / CONFIRMED / CANCELLED 预约的影响
 * - duration=90 → 17:00 不可选
 * - 相邻预约 / 部分重叠
 * - 不同员工同时间不互相影响
 * - 非法日期 → 400 + 日期格式错误
 * - 过期 slot 被过滤（用固定 Clock 控制）
 * - 未登录 → 401
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@Transactional
class AppointmentAvailabilityTest {

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

    // 用固定的"未来日期"，确保可预测：2030-01-01
    private static final LocalDate TARGET_DATE = LocalDate.of(2030, 1, 1);
    private static final String TARGET_DATE_STR = "2030-01-01";
    private static final ZoneId Z = ZoneId.of("Asia/Shanghai");

    // 每个测试方法前重置 Clock，避免上一个测试的状态泄露
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

    // ==================== 辅助 ====================

    private User createUser() {
        User u = new User();
        u.setUsername("avail_user_" + System.nanoTime());
        u.setPassword("x");
        u.setPhone("13800000000");
        u.setRole(Role.USER);
        return userRepository.save(u);
    }

    private Service createService(int duration) {
        Service s = new Service();
        s.setName("avail_svc_" + System.nanoTime());
        s.setDuration(duration);
        s.setPrice(100.0);
        return serviceRepository.save(s);
    }

    private Staff createStaff() {
        Staff s = new Staff();
        s.setName("avail_staff_" + System.nanoTime());
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

    // ==================== 一、基础校验 ====================

    @Test
    void availableSlots_staffNotExists_returns400() throws Exception {
        Service svc = createService(60);

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", "999999")
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("员工不存在"));
    }

    @Test
    void availableSlots_serviceNotExists_returns400() throws Exception {
        Staff staff = createStaff();

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", "999999")
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("服务不存在"));
    }

    @Test
    void availableSlots_staffDoesNotProvideService_returns400() throws Exception {
        Staff staff = createStaff();
        Service svc = createService(60);
        // 不创建 mapping

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该员工不支持此服务"));
    }

    // ==================== 二、正常场景 ====================

    @Test
    void availableSlots_noAppointmentsDuration60() throws Exception {
        Staff staff = createStaff();
        Service svc = createService(60);
        createMapping(staff.getId(), svc.getId());

        // 把时钟固定到 2030-01-01 00:00，避免 slot 被过期过滤
        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[0]", is("09:00")))
                .andExpect(jsonPath("$[1]", is("10:00")))
                .andExpect(jsonPath("$[2]", is("11:00")))
                .andExpect(jsonPath("$[3]", is("12:00")))
                .andExpect(jsonPath("$[4]", is("13:00")))
                .andExpect(jsonPath("$[5]", is("14:00")))
                .andExpect(jsonPath("$[6]", is("15:00")))
                .andExpect(jsonPath("$[7]", is("16:00")))
                .andExpect(jsonPath("$[8]", is("17:00")));
    }

    @Test
    void availableSlots_pendingAppointmentBlocksSlot() throws Exception {
        User user = createUser();
        Staff staff = createStaff();
        Service svc = createService(60);
        createMapping(staff.getId(), svc.getId());
        seedAppointment(user.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$", not(hasItem("10:00"))));
    }

    @Test
    void availableSlots_confirmedAppointmentBlocksSlot() throws Exception {
        User user = createUser();
        Staff staff = createStaff();
        Service svc = createService(60);
        createMapping(staff.getId(), svc.getId());
        seedAppointment(user.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.CONFIRMED);

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$", not(hasItem("10:00"))));
    }

    @Test
    void availableSlots_cancelledAppointmentDoesNotBlock() throws Exception {
        User user = createUser();
        Staff staff = createStaff();
        Service svc = createService(60);
        createMapping(staff.getId(), svc.getId());
        seedAppointment(user.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.CANCELLED);

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$", hasItem("10:00")));
    }

    // ==================== 三、duration 边界 ====================

    @Test
    void availableSlots_duration90_lastStartIs1630() throws Exception {
        Staff staff = createStaff();
        Service svc = createService(90);
        createMapping(staff.getId(), svc.getId());

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0]", is("09:00")))
                .andExpect(jsonPath("$[1]", is("10:30")))
                .andExpect(jsonPath("$[2]", is("12:00")))
                .andExpect(jsonPath("$[3]", is("13:30")))
                .andExpect(jsonPath("$[4]", is("15:00")))
                .andExpect(jsonPath("$[5]", is("16:30")))
                .andExpect(jsonPath("$", not(hasItem("17:00"))));
    }

    // ==================== 四、相邻与重叠 ====================

    @Test
    void availableSlots_adjacentAppointmentsAllowed() throws Exception {
        User user = createUser();
        Staff staff = createStaff();
        Service svc = createService(60);
        createMapping(staff.getId(), svc.getId());
        // 09:00-10:00 和 10:00-11:00 相邻
        seedAppointment(user.getId(), svc.getId(), staff.getId(),
                atTime(9, 0), AppointmentStatus.PENDING);
        seedAppointment(user.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$", not(hasItem("09:00"))))
                .andExpect(jsonPath("$", not(hasItem("10:00"))))
                .andExpect(jsonPath("$", hasItem("11:00")));
    }

    @Test
    void availableSlots_partialOverlapIsBlocked() throws Exception {
        User user = createUser();
        Staff staff = createStaff();
        Service svc = createService(60);
        createMapping(staff.getId(), svc.getId());
        // 已有 10:00-11:00，新预约 10:30-11:30 会冲突
        seedAppointment(user.getId(), svc.getId(), staff.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasItem("10:00"))))
                .andExpect(jsonPath("$", hasItem("11:00")));
    }

    // ==================== 五、不同员工互相独立 ====================

    @Test
    void availableSlots_differentStaffSameTimeIsIndependent() throws Exception {
        User user = createUser();
        Staff staffA = createStaff();
        Staff staffB = createStaff();
        Service svc = createService(60);
        createMapping(staffA.getId(), svc.getId());
        createMapping(staffB.getId(), svc.getId());
        // A 在 10:00 有预约
        seedAppointment(user.getId(), svc.getId(), staffA.getId(),
                atTime(10, 0), AppointmentStatus.PENDING);

        setFixedClockTo(atTime(0, 0));

        // 查 B → 不受 A 影响
        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staffB.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$", hasItem("10:00")));
    }

    // ==================== 六、非法日期 ====================

    @Test
    void availableSlots_invalidDate_returns400WithMessage() throws Exception {
        Staff staff = createStaff();
        Service svc = createService(60);
        createMapping(staff.getId(), svc.getId());

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("日期格式错误，应为 YYYY-MM-DD"));
    }

    // ==================== 七、过期 slot 过滤 ====================

    @Test
    void availableSlots_pastSlotsFilteredOut() throws Exception {
        Staff staff = createStaff();
        Service svc = createService(60);
        createMapping(staff.getId(), svc.getId());

        // 固定当前时间 = 2030-01-01 10:30 → 09:00 / 10:00 已过期，11:00 起有效
        setFixedClockTo(atTime(10, 30));

        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", String.valueOf(staff.getId()))
                        .param("serviceId", String.valueOf(svc.getId()))
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(hasItem("09:00"))))
                .andExpect(jsonPath("$", not(hasItem("10:00"))))
                .andExpect(jsonPath("$", hasItem("11:00")));
    }

    // ==================== 八、未登录 ====================

    @Test
    @WithAnonymousUser
    void availableSlots_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/appointments/available-slots")
                        .param("staffId", "1")
                        .param("serviceId", "1")
                        .param("date", TARGET_DATE_STR))
                .andExpect(status().isUnauthorized());
    }
}