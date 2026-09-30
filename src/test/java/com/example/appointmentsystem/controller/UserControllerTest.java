package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createUser_returnsUser_withoutPassword() throws Exception {
        String username = "alice_" + System.nanoTime();

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"secret123\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.phone").value("13800000000"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void findById_returnsUser_withoutPassword() throws Exception {
        User user = createUser("bob_" + System.nanoTime(), "pwd", "13900000000");

        mockMvc.perform(get("/users/" + user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.username").value(user.getUsername()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void findById_missing_returns404() throws Exception {
        mockMvc.perform(get("/users/999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void findByUsername_returnsMatchingUser() {
        String username = "carol_" + System.nanoTime();
        User user = createUser(username, "pwd", "13700000000");

        assertEquals(user.getId(),
                userRepository.findByUsername(username).orElseThrow().getId());
        assertEquals("USER",
                userRepository.findByUsername(username).orElseThrow().getRole());
        assertTrue(userRepository.findByUsername("nobody_" + System.nanoTime()).isEmpty());
    }

    @Test
    void createUser_passwordIsEncryptedInDatabase() throws Exception {
        String username = "dave_" + System.nanoTime();
        String rawPassword = "secret123";
        createViaApi(username, rawPassword, "13600000000");

        String stored = userRepository.findByUsername(username).orElseThrow().getPassword();

        assertFalse(rawPassword.equals(stored), "数据库中的密码不应为明文");
        assertTrue(stored.startsWith("$2"), "密码应为 BCrypt 格式（$2a/$2b/$2y 前缀）");
    }

    @Test
    void createUser_passwordMatchesOriginalPassword() throws Exception {
        String username = "eve_" + System.nanoTime();
        String rawPassword = "secret123";
        createViaApi(username, rawPassword, "13500000000");

        String stored = userRepository.findByUsername(username).orElseThrow().getPassword();

        assertTrue(passwordEncoder.matches(rawPassword, stored));
    }

    @Test
    void updateUser_passwordChange_oldNotMatch_newMatch() throws Exception {
        String username = "frank_" + System.nanoTime();
        String phone = "13400000000";
        String oldPassword = "oldPass";
        createViaApi(username, oldPassword, phone);

        Long id = userRepository.findByUsername(username).orElseThrow().getId();

        mockMvc.perform(put("/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"phone\":\"" + phone
                                + "\",\"password\":\"newPass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());

        String stored = userRepository.findByUsername(username).orElseThrow().getPassword();

        assertFalse(passwordEncoder.matches(oldPassword, stored), "旧密码不应再匹配");
        assertTrue(passwordEncoder.matches("newPass", stored), "新密码应可匹配");
    }

    private void createViaApi(String username, String password, String phone) throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"" + password
                                + "\",\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
    }

    private User createUser(String username, String password, String phone) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setPhone(phone);
        return userRepository.save(user);
    }
}
