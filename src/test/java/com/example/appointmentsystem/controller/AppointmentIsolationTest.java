package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.Appointment;
import com.example.appointmentsystem.entity.AppointmentStatus;
import com.example.appointmentsystem.entity.Role;
import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.repository.AppointmentRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppointmentIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void myAppointments_returnsOnlyCurrentUserAppointments() throws Exception {
        User userA = createUser("a_" + System.nanoTime(), "passA");
        User userB = createUser("b_" + System.nanoTime(), "passB");

        seedAppointment(userA.getId());
        seedAppointment(userB.getId());

        String tokenA = login(userA.getUsername(), "passA");

        mockMvc.perform(get("/appointments/my")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userA.getId()))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void findByUserId_otherUser_returns403() throws Exception {
        User userA = createUser("a_" + System.nanoTime(), "passA");
        User userB = createUser("b_" + System.nanoTime(), "passB");

        String tokenA = login(userA.getUsername(), "passA");

        mockMvc.perform(get("/appointments/user/" + userB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_otherUserAppointment_returns403() throws Exception {
        User userA = createUser("a_" + System.nanoTime(), "passA");
        User userB = createUser("b_" + System.nanoTime(), "passB");

        Appointment appointmentB = seedAppointment(userB.getId());
        String tokenA = login(userA.getUsername(), "passA");

        mockMvc.perform(put("/appointments/" + appointmentB.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_ownAppointment_returns200() throws Exception {
        User userA = createUser("a_" + System.nanoTime(), "passA");
        Appointment appointmentA = seedAppointment(userA.getId());

        String tokenA = login(userA.getUsername(), "passA");

        mockMvc.perform(put("/appointments/" + appointmentA.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private User createUser(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone("12000000000");
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private Appointment seedAppointment(Long userId) {
        Appointment appointment = new Appointment();
        appointment.setUserId(userId);
        appointment.setServiceId(1L);
        appointment.setStaffId(1L);
        appointment.setAppointmentTime(LocalDateTime.now().plusDays(1));
        appointment.setStatus(AppointmentStatus.PENDING);
        return appointmentRepository.save(appointment);
    }

    private String login(String username, String password) throws Exception {
        String json = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
