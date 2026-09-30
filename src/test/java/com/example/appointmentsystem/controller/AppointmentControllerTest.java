package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.Appointment;
import com.example.appointmentsystem.entity.AppointmentStatus;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AppointmentController 核心业务流程集成测试。
 *
 * 使用独立的 test profile（application-test.properties）：
 * - 连接 appointment_system_test 数据库，createDatabaseIfNotExist 自动建库
 * - ddl-auto=create-drop 自动建表并在测试结束后清理
 * - 每个测试方法通过 @Transactional 回滚，保证测试数据互不影响、不依赖已有数据
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@Transactional
class AppointmentControllerTest {

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

    // ==================== 创建预约 ====================

    @Test
    void createAppointment_returns200WithIdAndStatus() throws Exception {
        User user = createUser();
        Service service = createService(30);
        Staff staff = createStaff();
        createMapping(staff.getId(), service.getId());

        mockMvc.perform(post("/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(
                                user.getId(),
                                service.getId(),
                                staff.getId(),
                                futureBusinessTime(),
                                AppointmentStatus.PENDING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.serviceId").value(service.getId()))
                .andExpect(jsonPath("$.staffId").value(staff.getId()));
    }

    // ==================== 查询预约 ====================

    @Test
    void findById_whenExists_returns200() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.PENDING);

        mockMvc.perform(get("/appointments/" + appointment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointment.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void findById_whenMissing_returns404() throws Exception {
        mockMvc.perform(get("/appointments/999999999"))
                .andExpect(status().isNotFound());
    }

    // ==================== 确认预约 ====================

    @Test
    void confirm_whenPending_returnsConfirmed() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.PENDING);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirm_whenConfirmed_returns400() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.CONFIRMED);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有待确认的预约才能确认"));
    }

    @Test
    void confirm_whenCancelled_returns400() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.CANCELLED);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有待确认的预约才能确认"));
    }

    @Test
    void confirm_whenCompleted_returns400() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.COMPLETED);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有待确认的预约才能确认"));
    }

    // ==================== 完成预约 ====================

    @Test
    void complete_whenConfirmed_returnsCompleted() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.CONFIRMED);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void complete_whenPending_returns400() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.PENDING);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有已确认的预约才能完成"));
    }

    @Test
    void complete_whenCancelled_returns400() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.CANCELLED);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有已确认的预约才能完成"));
    }

    @Test
    void complete_whenCompleted_returns400() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.COMPLETED);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有已确认的预约才能完成"));
    }

    // ==================== 取消预约 ====================

    @Test
    void cancel_whenPending_returnsCancelled() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.PENDING);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_whenConfirmed_returnsCancelled() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.CONFIRMED);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_whenCompleted_returns400() throws Exception {
        Appointment appointment = seedAppointment(AppointmentStatus.COMPLETED);

        mockMvc.perform(put("/appointments/" + appointment.getId() + "/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有待确认或已确认的预约才能取消"));
    }

    // ==================== 数据与工具方法 ====================

    private User createUser() {
        User user = new User();
        user.setUsername("user");
        user.setPassword("password");
        user.setPhone("138" + System.nanoTime());
        return userRepository.save(user);
    }

    private Service createService(int durationMinutes) {
        Service service = new Service();
        service.setName("test_service_" + System.nanoTime());
        service.setDuration(durationMinutes);
        service.setPrice(100.0);
        return serviceRepository.save(service);
    }

    private Staff createStaff() {
        Staff staff = new Staff();
        staff.setName("test_staff_" + System.nanoTime());
        staff.setPhone("139" + System.nanoTime());
        return staffRepository.save(staff);
    }

    private StaffServiceMapping createMapping(Long staffId, Long serviceId) {
        return staffServiceMappingRepository.save(
                new StaffServiceMapping(new StaffServiceId(staffId, serviceId)));
    }

    private Appointment seedAppointment(AppointmentStatus status) {
        User user = createUser();
        Service service = createService(30);
        Staff staff = createStaff();
        createMapping(staff.getId(), service.getId());

        Appointment appointment = new Appointment();
        appointment.setUserId(user.getId());
        appointment.setServiceId(service.getId());
        appointment.setStaffId(staff.getId());
        appointment.setAppointmentTime(futureBusinessTime());
        appointment.setStatus(status);
        return appointmentRepository.save(appointment);
    }

    private LocalDateTime futureBusinessTime() {
        return LocalDateTime.now()
                .plusDays(1)
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }

    private String appointmentBody(Long userId, Long serviceId, Long staffId,
                                   LocalDateTime appointmentTime,
                                   AppointmentStatus status) {
        String time = appointmentTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        StringBuilder body = new StringBuilder();
        body.append("{\"userId\":").append(userId)
                .append(",\"serviceId\":").append(serviceId)
                .append(",\"staffId\":").append(staffId)
                .append(",\"appointmentTime\":\"").append(time).append("\"");
        if (status != null) {
            body.append(",\"status\":\"").append(status.name()).append("\"");
        }
        body.append("}");
        return body.toString();
    }

}
