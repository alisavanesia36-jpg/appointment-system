package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.Role;
import com.example.appointmentsystem.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class UserAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== User 权限 ====================

    @Test
    void user_findAll_returns403() throws Exception {
        User user = createUser("u1_" + nano(), "pass", Role.USER);
        String token = login(user.getUsername(), "pass");

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_findAll_returns200() throws Exception {
        User admin = createUser("a1_" + nano(), "pass", Role.ADMIN);
        String token = login(admin.getUsername(), "pass");

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_findById_own_returns200() throws Exception {
        User user = createUser("u2_" + nano(), "pass", Role.USER);
        String token = login(user.getUsername(), "pass");

        mockMvc.perform(get("/users/" + user.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()));
    }

    @Test
    void user_findById_other_returns403() throws Exception {
        User userA = createUser("u3_" + nano(), "pass", Role.USER);
        User userB = createUser("u3b_" + nano(), "pass", Role.USER);
        String token = login(userA.getUsername(), "pass");

        mockMvc.perform(get("/users/" + userB.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_findById_other_returns200() throws Exception {
        User admin = createUser("a4_" + nano(), "pass", Role.ADMIN);
        User other = createUser("u4_" + nano(), "pass", Role.USER);
        String token = login(admin.getUsername(), "pass");

        mockMvc.perform(get("/users/" + other.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(other.getId()));
    }

    @Test
    void user_delete_returns403() throws Exception {
        User user = createUser("u5_" + nano(), "pass", Role.USER);
        User target = createUser("u5b_" + nano(), "pass", Role.USER);
        String token = login(user.getUsername(), "pass");

        mockMvc.perform(delete("/users/" + target.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_delete_returns200() throws Exception {
        User admin = createUser("a6_" + nano(), "pass", Role.ADMIN);
        User target = createUser("u6_" + nano(), "pass", Role.USER);
        String token = login(admin.getUsername(), "pass");

        mockMvc.perform(delete("/users/" + target.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void user_update_returns403() throws Exception {
        User user = createUser("u7_" + nano(), "pass", Role.USER);
        User target = createUser("u7b_" + nano(), "pass", Role.USER);
        String token = login(user.getUsername(), "pass");

        mockMvc.perform(put("/users/" + target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13911112222\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_update_returns200() throws Exception {
        User admin = createUser("a8_" + nano(), "pass", Role.ADMIN);
        User target = createUser("u8_" + nano(), "pass", Role.USER);
        String token = login(admin.getUsername(), "pass");

        mockMvc.perform(put("/users/" + target.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13911112222\",\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("13911112222"));
    }

    // ==================== /users/me ====================

    @Test
    void user_me_returnsCurrentUser() throws Exception {
        User user = createUser("me_" + nano(), "pass", Role.USER);
        String token = login(user.getUsername(), "pass");

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.username").value(user.getUsername()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void user_updateMe_phone_success() throws Exception {
        User user = createUser("me2_" + nano(), "pass", Role.USER);
        String token = login(user.getUsername(), "pass");

        mockMvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13911112222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("13911112222"));
    }

    @Test
    void user_updateMe_username_success() throws Exception {
        User user = createUser("me3_" + nano(), "pass", Role.USER);
        String token = login(user.getUsername(), "pass");
        String newUsername = "newname_" + nano();

        mockMvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + newUsername + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(newUsername));

        assertTrue(userRepository.findByUsername(newUsername).isPresent());
    }

    @Test
    void user_updateMe_password_oldNotMatch_newMatch() throws Exception {
        User user = createUser("me4_" + nano(), "oldPass", Role.USER);
        String token = login(user.getUsername(), "oldPass");

        mockMvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"newPass\"}"))
                .andExpect(status().isOk());

        String stored = userRepository.findByUsername(user.getUsername())
                .orElseThrow().getPassword();
        assertFalse(passwordEncoder.matches("oldPass", stored));
        assertTrue(passwordEncoder.matches("newPass", stored));
    }

    @Test
    void user_updateMe_roleInBody_staysUser() throws Exception {
        User user = createUser("me5_" + nano(), "pass", Role.USER);
        String token = login(user.getUsername(), "pass");

        mockMvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        assertEquals(Role.USER,
                userRepository.findByUsername(user.getUsername()).orElseThrow().getRole());
    }

    @Test
    void user_updateMe_idInBody_unchanged() throws Exception {
        User user = createUser("me6_" + nano(), "pass", Role.USER);
        String token = login(user.getUsername(), "pass");

        mockMvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":999999}"))
                .andExpect(status().isOk());

        assertEquals(user.getId(),
                userRepository.findByUsername(user.getUsername()).orElseThrow().getId());
    }

    // ==================== 注册 ====================

    @Test
    void register_success_returnsUserWithoutPassword() throws Exception {
        String username = "reg_" + nano();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, "pass123", "13800000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_passwordEncrypted_andMatches() throws Exception {
        String username = "regp_" + nano();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, "pass123", "13800000000")))
                .andExpect(status().isOk());

        String stored = userRepository.findByUsername(username).orElseThrow().getPassword();
        assertFalse("pass123".equals(stored));
        assertTrue(passwordEncoder.matches("pass123", stored));
    }

    @Test
    void register_duplicateUsername_returns400() throws Exception {
        String username = "dup_" + nano();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, "pass123", "13800000000")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, "pass456", "13900000000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    void register_withAdminRole_staysUser() throws Exception {
        String username = "regr_" + nano();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"pass123\",\"phone\":\"13800000000\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        assertEquals(Role.USER,
                userRepository.findByUsername(username).orElseThrow().getRole());
    }

    @Test
    void register_withoutJwt_isAllowed() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("noreg_" + nano(), "pass123", "13800000000")))
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

    private String registerBody(String username, String password, String phone) {
        return "{\"username\":\"" + username
                + "\",\"password\":\"" + password
                + "\",\"phone\":\"" + phone + "\"}";
    }
}
