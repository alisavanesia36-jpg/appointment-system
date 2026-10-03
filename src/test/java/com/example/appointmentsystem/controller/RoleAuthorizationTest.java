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
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private StaffServiceMappingRepository staffServiceMappingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== 一、查询全部 / 状态 / 时间范围 ====================

    @Test
    void user_findAll_returns403() throws Exception {
        String token = loginAndGetToken(createUser("u1_" + nano(), "pass", Role.USER));

        mockMvc.perform(get("/appointments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_findAll_returns200() throws Exception {
        String token = loginAndGetToken(createUser("a1_" + nano(), "pass", Role.ADMIN));

        mockMvc.perform(get("/appointments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_findByStatus_returns403() throws Exception {
        String token = loginAndGetToken(createUser("u2_" + nano(), "pass", Role.USER));

        mockMvc.perform(get("/appointments/status/PENDING")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_findByStatus_returns200() throws Exception {
        String token = loginAndGetToken(createUser("a2_" + nano(), "pass", Role.ADMIN));

        mockMvc.perform(get("/appointments/status/PENDING")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_findByTimeRange_returns403() throws Exception {
        String token = loginAndGetToken(createUser("u3_" + nano(), "pass", Role.USER));

        mockMvc.perform(get("/appointments/time-range")
                        .param("start", "2026-01-01T00:00:00")
                        .param("end", "2026-12-31T23:59:59")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_findByTimeRange_returns200() throws Exception {
        String token = loginAndGetToken(createUser("a3_" + nano(), "pass", Role.ADMIN));

        mockMvc.perform(get("/appointments/time-range")
                        .param("start", "2026-01-01T00:00:00")
                        .param("end", "2026-12-31T23:59:59")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ==================== 二、查询详情 ====================

    @Test
    void user_findById_own_returns200() throws Exception {
        User user = createUser("u4_" + nano(), "pass", Role.USER);
        Appointment appointment = seedAppointment(user.getId(), AppointmentStatus.PENDING);
        String token = loginAndGetToken(user);

        mockMvc.perform(get("/appointments/" + appointment.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_findById_other_returns403() throws Exception {
        User userA = createUser("u5_" + nano(), "pass", Role.USER);
        User userB = createUser("u5b_" + nano(), "pass", Role.USER);
        Appointment appointmentB = seedAppointment(userB.getId(), AppointmentStatus.PENDING);
        String token = loginAndGetToken(userA);

        mockMvc.perform(get("/appointments/" + appointmentB.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ==================== 三、修改 ====================

    @Test
    void user_update_own_returns200() throws Exception {
        User user = createUser("u6_" + nano(), "pass", Role.USER);
        Appointment appointment = seedAppointmentWithService(user.getId(), AppointmentStatus.PENDING);
        String token = loginAndGetToken(user);

        mockMvc.perform(put("/appointments/" + appointment.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(appointment)))
                .andExpect(status().isOk());
    }

    @Test
    void user_update_other_returns403() throws Exception {
        User userA = createUser("u7_" + nano(), "pass", Role.USER);
        User userB = createUser("u7b_" + nano(), "pass", Role.USER);
        Appointment appointmentB = seedAppointment(userB.getId(), AppointmentStatus.PENDING);
        String token = loginAndGetToken(userA);

        mockMvc.perform(put("/appointments/" + appointmentB.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ==================== 四、删除 ====================

    @Test
    void user_delete_returns403() throws Exception {
        User user = createUser("u8_" + nano(), "pass", Role.USER);
        Appointment appointment = seedAppointment(user.getId(), AppointmentStatus.PENDING);
        String token = loginAndGetToken(user);

        mockMvc.perform(delete("/appointments/" + appointment.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_delete_returns200() throws Exception {
        User admin = createUser("a9_" + nano(), "pass", Role.ADMIN);
        Appointment appointment = seedAppointment(admin.getId(), AppointmentStatus.PENDING);
        String token = loginAndGetToken(admin);

        mockMvc.perform(delete("/appointments/" + appointment.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ==================== 五、confirm / complete ====================

    @Test
    void user_confirm_returns403() throws Exception {
        User user = createUser("u10_" + nano(), "pass", Role.USER);
        Appointment appointment = seedAppointment(user.getId(), AppointmentStatus.PENDING);
        String token = loginAndGetToken(user);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_confirm_pending_returnsConfirmed() throws Exception {
        User admin = createUser("a11_" + nano(), "pass", Role.ADMIN);
        Appointment appointment = seedAppointment(admin.getId(), AppointmentStatus.PENDING);
        String token = loginAndGetToken(admin);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void user_complete_returns403() throws Exception {
        User user = createUser("u12_" + nano(), "pass", Role.USER);
        Appointment appointment = seedAppointment(user.getId(), AppointmentStatus.CONFIRMED);
        String token = loginAndGetToken(user);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_complete_confirmed_returnsCompleted() throws Exception {
        User admin = createUser("a13_" + nano(), "pass", Role.ADMIN);
        Appointment appointment = seedAppointment(admin.getId(), AppointmentStatus.CONFIRMED);
        String token = loginAndGetToken(admin);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ==================== 六、按用户查询 ====================

    @Test
    void user_findByUserId_other_returns403() throws Exception {
        User userA = createUser("u14_" + nano(), "pass", Role.USER);
        User userB = createUser("u14b_" + nano(), "pass", Role.USER);
        String token = loginAndGetToken(userA);

        mockMvc.perform(get("/appointments/user/" + userB.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_findByUserId_own_returns200() throws Exception {
        User user = createUser("u15_" + nano(), "pass", Role.USER);
        seedAppointment(user.getId(), AppointmentStatus.PENDING);
        String token = loginAndGetToken(user);

        mockMvc.perform(get("/appointments/user/" + user.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_findUnfinished_other_returns403() throws Exception {
        User userA = createUser("u16_" + nano(), "pass", Role.USER);
        User userB = createUser("u16b_" + nano(), "pass", Role.USER);
        String token = loginAndGetToken(userA);

        mockMvc.perform(get("/appointments/user/" + userB.getId() + "/unfinished")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_findUnfinished_own_returns200() throws Exception {
        User user = createUser("u17_" + nano(), "pass", Role.USER);
        seedAppointment(user.getId(), AppointmentStatus.PENDING);
        String token = loginAndGetToken(user);

        mockMvc.perform(get("/appointments/user/" + user.getId() + "/unfinished")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ==================== 工具方法 ====================

    private String nano() {
        return String.valueOf(System.nanoTime());
    }

    private User createUser(String username, String password, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone("12000000000");
        user.setRole(role);
        return userRepository.save(user);
    }

    private String loginAndGetToken(User user) throws Exception {
        String json = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.getUsername()
                                + "\",\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    private Appointment seedAppointment(Long userId, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setUserId(userId);
        appointment.setServiceId(1L);
        appointment.setStaffId(1L);
        appointment.setAppointmentTime(futureTime());
        appointment.setStatus(status);
        return appointmentRepository.save(appointment);
    }

    private Appointment seedAppointmentWithService(Long userId, AppointmentStatus status) {
        Service service = new Service();
        service.setName("svc_" + System.nanoTime());
        service.setDuration(30);
        service.setPrice(100.0);
        serviceRepository.save(service);

        Staff staff = new Staff();
        staff.setName("staff_" + System.nanoTime());
        staff.setPhone("13900000000");
        staffRepository.save(staff);

        staffServiceMappingRepository.save(
                new StaffServiceMapping(new StaffServiceId(staff.getId(), service.getId())));

        Appointment appointment = new Appointment();
        appointment.setUserId(userId);
        appointment.setServiceId(service.getId());
        appointment.setStaffId(staff.getId());
        appointment.setAppointmentTime(futureTime());
        appointment.setStatus(status);
        return appointmentRepository.save(appointment);
    }

    private LocalDateTime futureTime() {
        return LocalDateTime.now().plusDays(1)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    private String appointmentBody(Appointment appointment) {
        String time = appointment.getAppointmentTime()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return "{\"userId\":" + appointment.getUserId()
                + ",\"serviceId\":" + appointment.getServiceId()
                + ",\"staffId\":" + appointment.getStaffId()
                + ",\"appointmentTime\":\"" + time + "\""
                + ",\"status\":\"" + appointment.getStatus().name() + "\"}";
    }
}
