package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.Role;
import com.example.appointmentsystem.entity.Service;
import com.example.appointmentsystem.entity.Staff;
import com.example.appointmentsystem.entity.StaffServiceId;
import com.example.appointmentsystem.entity.StaffServiceMapping;
import com.example.appointmentsystem.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v1.5 后台写权限加固测试。
 *
 * 覆盖：
 * - ServiceController / StaffController / StaffServiceMappingController 的写操作权限矩阵
 * - USER：可读（GET），写操作（POST/PUT/DELETE）必须 403
 * - ADMIN：写操作必须不被权限拦截（业务上可成功落地）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ServiceStaffAuthorizationTest {

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
    private PasswordEncoder passwordEncoder;

    // ==================== 一、USER 读权限（必须 200）====================

    @Test
    void user_listServices_returns200() throws Exception {
        String token = login(createUser("u_sv_list", Role.USER));
        mockMvc.perform(get("/services")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_getServiceById_returns200() throws Exception {
        Service service = seedService();
        String token = login(createUser("u_sv_one", Role.USER));
        mockMvc.perform(get("/services/" + service.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_listStaff_returns200() throws Exception {
        String token = login(createUser("u_st_list", Role.USER));
        mockMvc.perform(get("/staff")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_getStaffById_returns200() throws Exception {
        Staff staff = seedStaff();
        String token = login(createUser("u_st_one", Role.USER));
        mockMvc.perform(get("/staff/" + staff.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_listStaffServiceMappings_returns200() throws Exception {
        String token = login(createUser("u_ssm_list", Role.USER));
        mockMvc.perform(get("/staff-services")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_findStaffServiceMappingsByStaff_returns200() throws Exception {
        Staff staff = seedStaff();
        Service service = seedService();
        seedMapping(staff.getId(), service.getId());

        String token = login(createUser("u_ssm_st", Role.USER));
        mockMvc.perform(get("/staff-services/staff/" + staff.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_findStaffServiceMappingsByService_returns200() throws Exception {
        Staff staff = seedStaff();
        Service service = seedService();
        seedMapping(staff.getId(), service.getId());

        String token = login(createUser("u_ssm_sv", Role.USER));
        mockMvc.perform(get("/staff-services/service/" + service.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ==================== 二、USER 写权限（必须 403）====================

    @Test
    void user_createService_returns403() throws Exception {
        String token = login(createUser("u_sv_create", Role.USER));
        mockMvc.perform(post("/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_updateService_returns403() throws Exception {
        Service service = seedService();
        String token = login(createUser("u_sv_update", Role.USER));
        mockMvc.perform(put("/services/" + service.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_deleteService_returns403() throws Exception {
        Service service = seedService();
        String token = login(createUser("u_sv_delete", Role.USER));
        mockMvc.perform(delete("/services/" + service.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        assertTrue(serviceRepository.findById(service.getId()).isPresent());
    }

    @Test
    void user_createStaff_returns403() throws Exception {
        String token = login(createUser("u_st_create", Role.USER));
        mockMvc.perform(post("/staff")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_deleteStaff_returns403() throws Exception {
        Staff staff = seedStaff();
        String token = login(createUser("u_st_delete", Role.USER));
        mockMvc.perform(delete("/staff/" + staff.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        assertTrue(staffRepository.findById(staff.getId()).isPresent());
    }

    @Test
    void user_createStaffServiceMapping_returns403() throws Exception {
        Staff staff = seedStaff();
        Service service = seedService();

        String token = login(createUser("u_ssm_create", Role.USER));
        mockMvc.perform(post("/staff-services/" + staff.getId() + "/" + service.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        assertFalse(staffServiceMappingRepository
                .findById(new StaffServiceId(staff.getId(), service.getId())).isPresent());
    }

    @Test
    void user_deleteStaffServiceMapping_returns403() throws Exception {
        Staff staff = seedStaff();
        Service service = seedService();
        seedMapping(staff.getId(), service.getId());

        String token = login(createUser("u_ssm_delete", Role.USER));
        mockMvc.perform(delete("/staff-services/" + staff.getId() + "/" + service.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        assertTrue(staffServiceMappingRepository
                .findById(new StaffServiceId(staff.getId(), service.getId())).isPresent());
    }

    // ==================== 三、ADMIN 写权限（不应被权限拦截）====================

    @Test
    void admin_createService_notForbidden() throws Exception {
        String token = login(createUser("a_sv_create", Role.ADMIN));
        mockMvc.perform(post("/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceBody()))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 403) {
                        throw new AssertionError("ADMIN POST /services 被权限拦截");
                    }
                });
    }

    @Test
    void admin_updateService_notForbidden() throws Exception {
        Service service = seedService();
        String token = login(createUser("a_sv_update", Role.ADMIN));
        mockMvc.perform(put("/services/" + service.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceBody()))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 403) {
                        throw new AssertionError("ADMIN PUT /services/{id} 被权限拦截");
                    }
                });
    }

    @Test
    void admin_deleteService_notForbidden() throws Exception {
        Service service = seedService();
        String token = login(createUser("a_sv_delete", Role.ADMIN));
        mockMvc.perform(delete("/services/" + service.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 403) {
                        throw new AssertionError("ADMIN DELETE /services/{id} 被权限拦截");
                    }
                });
    }

    @Test
    void admin_createStaff_notForbidden() throws Exception {
        String token = login(createUser("a_st_create", Role.ADMIN));
        mockMvc.perform(post("/staff")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffBody()))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 403) {
                        throw new AssertionError("ADMIN POST /staff 被权限拦截");
                    }
                });
    }

    @Test
    void admin_deleteStaff_notForbidden() throws Exception {
        Staff staff = seedStaff();
        String token = login(createUser("a_st_delete", Role.ADMIN));
        mockMvc.perform(delete("/staff/" + staff.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 403) {
                        throw new AssertionError("ADMIN DELETE /staff/{id} 被权限拦截");
                    }
                });
    }

    @Test
    void admin_createStaffServiceMapping_notForbidden() throws Exception {
        Staff staff = seedStaff();
        Service service = seedService();

        String token = login(createUser("a_ssm_create", Role.ADMIN));
        mockMvc.perform(post("/staff-services/" + staff.getId() + "/" + service.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 403) {
                        throw new AssertionError("ADMIN POST /staff-services/{staffId}/{serviceId} 被权限拦截");
                    }
                });
    }

    @Test
    void admin_deleteStaffServiceMapping_notForbidden() throws Exception {
        Staff staff = seedStaff();
        Service service = seedService();
        seedMapping(staff.getId(), service.getId());

        String token = login(createUser("a_ssm_delete", Role.ADMIN));
        mockMvc.perform(delete("/staff-services/" + staff.getId() + "/" + service.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 403) {
                        throw new AssertionError("ADMIN DELETE /staff-services/{staffId}/{serviceId} 被权限拦截");
                    }
                });
    }

    // ==================== 工具方法 ====================

    private String nano() {
        return String.valueOf(System.nanoTime());
    }

    private User createUser(String prefix, Role role) {
        User user = new User();
        user.setUsername(prefix + "_" + nano());
        user.setPassword(passwordEncoder.encode("pass"));
        user.setPhone("12000000000");
        user.setRole(role);
        return userRepository.save(user);
    }

    private String login(User user) throws Exception {
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

    private Service seedService() {
        Service service = new Service();
        service.setName("svc_" + nano());
        service.setDuration(30);
        service.setPrice(100.0);
        return serviceRepository.save(service);
    }

    private Staff seedStaff() {
        Staff staff = new Staff();
        staff.setName("staff_" + nano());
        staff.setPhone("13900000000");
        return staffRepository.save(staff);
    }

    private StaffServiceMapping seedMapping(Long staffId, Long serviceId) {
        return staffServiceMappingRepository.save(
                new StaffServiceMapping(new StaffServiceId(staffId, serviceId)));
    }

    private String serviceBody() {
        return "{\"name\":\"svc_test_" + nano() + "\",\"duration\":30,\"price\":99.0}";
    }

    private String staffBody() {
        return "{\"name\":\"staff_test_" + nano() + "\",\"phone\":\"13900000000\"}";
    }
}
