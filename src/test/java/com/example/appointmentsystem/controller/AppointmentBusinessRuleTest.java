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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Appointment 第二阶段业务规则测试：
 * 时间冲突、相邻时间、不同员工、update 冲突与自排除、update 状态机绕过。
 *
 * 使用独立 test profile（application-test.properties）+ @Transactional 回滚，
 * 测试数据在用例内创建，不依赖已有数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@Transactional
class AppointmentBusinessRuleTest {

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

    // ==================== 一、预约时间冲突 ====================

    @Test
    void createAppointment_sameStaffOverlappingTime_returns400() throws Exception {
        User user = createUser();
        Service service = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), service.getId());

        LocalDateTime start = futureTimeAt(10, 0);
        seedAppointment(user.getId(), service.getId(), staff.getId(),
                start, AppointmentStatus.PENDING);

        mockMvc.perform(post("/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(user.getId(), service.getId(),
                                staff.getId(), start, AppointmentStatus.PENDING)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("这个员工在这个时间段已经有预约了"));
    }

    @Test
    void createAppointment_sameStaffAdjacentTime_isAllowed() throws Exception {
        User user = createUser();
        Service service = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), service.getId());

        seedAppointment(user.getId(), service.getId(), staff.getId(),
                futureTimeAt(10, 0), AppointmentStatus.PENDING);

        // 相邻时间（前一个 10:00-11:00，新预约 11:00 开始）：
        // 当前实现 newStart.isBefore(existingEnd) 为 false，因此允许。
        mockMvc.perform(post("/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(user.getId(), service.getId(),
                                staff.getId(), futureTimeAt(11, 0), AppointmentStatus.PENDING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createAppointment_differentStaffSameTime_isAllowed() throws Exception {
        User user = createUser();
        Service service = createService(60);
        Staff staffA = createStaff();
        Staff staffB = createStaff();
        createMapping(staffA.getId(), service.getId());
        createMapping(staffB.getId(), service.getId());

        seedAppointment(user.getId(), service.getId(), staffA.getId(),
                futureTimeAt(10, 0), AppointmentStatus.PENDING);

        mockMvc.perform(post("/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(user.getId(), service.getId(),
                                staffB.getId(), futureTimeAt(10, 0), AppointmentStatus.PENDING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffId").value(staffB.getId()));
    }

    // ==================== 二、update 接口 ====================

    @Test
    void updateAppointment_toConflictingTime_returns400() throws Exception {
        User user = createUser();
        Service service = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), service.getId());

        seedAppointment(user.getId(), service.getId(), staff.getId(),
                futureTimeAt(10, 0), AppointmentStatus.PENDING);
        Appointment target = seedAppointment(user.getId(), service.getId(),
                staff.getId(), futureTimeAt(13, 0), AppointmentStatus.PENDING);

        mockMvc.perform(put("/appointments/" + target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(user.getId(), service.getId(),
                                staff.getId(), futureTimeAt(10, 0), AppointmentStatus.PENDING)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("这个员工在这个时间段已经有预约了"));
    }

    @Test
    void updateAppointment_selfTimeUnchanged_noSelfConflict() throws Exception {
        User user = createUser();
        Service service = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), service.getId());

        Appointment target = seedAppointment(user.getId(), service.getId(),
                staff.getId(), futureTimeAt(10, 0), AppointmentStatus.PENDING);

        // 时间不变，应排除自身、不误报时间冲突；update 不修改 status，
        // 即使传入 CONFIRMED，状态仍保持原值 PENDING。
        mockMvc.perform(put("/appointments/" + target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(target.getUserId(), target.getServiceId(),
                                target.getStaffId(), target.getAppointmentTime(),
                                AppointmentStatus.CONFIRMED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ==================== 三、update 状态保护 ====================

    @Test
    void updateAppointment_statusIsIgnored_keepsPending() throws Exception {
        User user = createUser();
        Service service = createService(60);
        Staff staff = createStaff();
        createMapping(staff.getId(), service.getId());

        Appointment target = seedAppointment(user.getId(), service.getId(),
                staff.getId(), futureTimeAt(10, 0), AppointmentStatus.PENDING);

        // update 不修改 status：传入 COMPLETED 后，状态仍应保持原值 PENDING。
        mockMvc.perform(put("/appointments/" + target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(target.getUserId(), target.getServiceId(),
                                target.getStaffId(), target.getAppointmentTime(),
                                AppointmentStatus.COMPLETED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ==================== 数据与工具方法 ====================

    private User createUser() {
        User user = new User();
        user.setUsername("test_user_" + System.nanoTime());
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

    private Appointment seedAppointment(Long userId, Long serviceId, Long staffId,
                                        LocalDateTime time, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setUserId(userId);
        appointment.setServiceId(serviceId);
        appointment.setStaffId(staffId);
        appointment.setAppointmentTime(time);
        appointment.setStatus(status);
        return appointmentRepository.save(appointment);
    }

    private LocalDateTime futureTimeAt(int hour, int minute) {
        return LocalDateTime.now()
                .plusDays(1)
                .withHour(hour)
                .withMinute(minute)
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
