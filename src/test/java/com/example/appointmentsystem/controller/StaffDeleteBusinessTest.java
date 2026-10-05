package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.Role;
import com.example.appointmentsystem.entity.Staff;
import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.repository.StaffRepository;
import com.example.appointmentsystem.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v1.7.2 bug fix 回归测试：
 * 删除存在外键关联的员工时，期望返回 400 + 业务 message，
 * 而不是 401 "未认证或 token 无效"。
 *
 * 关于测试库 FK 说明：
 * 测试 profile 用 ddl-auto=create-drop 自动建表，但 Appointment / StaffServiceMapping 实体
 * 没有声明 @ManyToOne，生成的 appointments.staff_id 与 staff_services.id.staff_id
 * 都是裸 Long 列，没有 FK 约束。因此真实关联数据无法触发 DataIntegrityViolationException。
 *
 * 真实外键关联（staff_services、appointments）的错误处理走同一个 Spring DAO 异常链：
 * Hibernate 抛 ConstraintViolationException → Spring 翻译为 DataIntegrityViolationException。
 * 只要 StaffService.deleteById 能正确捕获 DataIntegrityViolationException 并转 BusinessException，
 * 两种关联都会被同一段代码覆盖（GlobalExceptionHandler 处理 BusinessException → HTTP 400）。
 *
 * 因此本测试使用 @MockitoSpyBean 包裹真实 StaffRepository，
 * 默认所有方法走真实 Spring Data JPA 实现，仅 stub deleteById 让其抛 DataIntegrityViolationException，
 * 模拟生产环境中两种关联触发的异常路径。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaffDeleteBusinessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearSpy() {
        reset(staffRepository);
    }

    // ==================== 一、删除无关联员工 → 200 ====================

    @Test
    void admin_deleteStaff_withoutRelations_returns200_andStaffIsRemoved() throws Exception {
        Staff staff = seedStaff();

        // spy 默认走真实实现，deleteById/flush 都不抛
        doNothing().when(staffRepository).deleteById(staff.getId());

        String token = login(createUser("a_stf_del_ok", Role.ADMIN));

        mockMvc.perform(delete("/staff/" + staff.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("删除成功"));
    }

    // ==================== 二、删除时模拟 staff_services 外键冲突 → 400 ====================

    @Test
    void admin_deleteStaff_simulateFKConstraintFromStaffServiceMapping_returns400() throws Exception {
        Staff staff = seedStaff();

        // 模拟 staff_services 外键约束被触发
        doThrow(new DataIntegrityViolationException(
                "Cannot delete or update a parent row: a foreign key constraint fails " +
                        "(`appointment_system`.`staff_services`, CONSTRAINT `FK...` " +
                        "FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`))"))
                .when(staffRepository).deleteById(staff.getId());

        String token = login(createUser("a_stf_del_ssm", Role.ADMIN));

        mockMvc.perform(delete("/staff/" + staff.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("无法删除")));
    }

    // ==================== 三、删除时模拟 appointments 外键冲突 → 400 ====================

    @Test
    void admin_deleteStaff_simulateFKConstraintFromAppointment_returns400() throws Exception {
        Staff staff = seedStaff();

        // 模拟 appointments 外键约束被触发
        doThrow(new DataIntegrityViolationException(
                "Cannot delete or update a parent row: a foreign key constraint fails " +
                        "(`appointment_system`.`appointments`, CONSTRAINT `FK...` " +
                        "FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`))"))
                .when(staffRepository).deleteById(staff.getId());

        String token = login(createUser("a_stf_del_ap", Role.ADMIN));

        mockMvc.perform(delete("/staff/" + staff.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("无法删除")));
    }

    // ==================== 四、删除不存在的员工 → 400 ====================

    @Test
    void admin_deleteStaff_notExist_returns400_andMessageMentionsNotExist() throws Exception {
        // existsById 走真实 JPA，999999999 不存在 → 返回 false
        String token = login(createUser("a_stf_del_404", Role.ADMIN));

        mockMvc.perform(delete("/staff/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("员工不存在")));
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
                        .contentType("application/json")
                        .content("{\"username\":\"" + user.getUsername()
                                + "\",\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    private Staff seedStaff() {
        Staff staff = new Staff();
        staff.setName("staff_" + nano());
        staff.setPhone("13900000000");
        return staffRepository.save(staff);
    }
}