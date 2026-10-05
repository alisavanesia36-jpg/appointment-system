package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.Notification;
import com.example.appointmentsystem.entity.NotificationType;
import com.example.appointmentsystem.entity.Role;
import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.repository.NotificationRepository;
import com.example.appointmentsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v2.2 第一阶段：NotificationController 端点测试。
 *
 * <p>覆盖用户列出的 7 个用例：
 * <ol>
 *   <li>USER 获取自己的通知</li>
 *   <li>USER 看不到其它用户通知</li>
 *   <li>未登录 GET /notifications → 401</li>
 *   <li>unread count 正确</li>
 *   <li>单条已读</li>
 *   <li>USER 不能把其它用户通知标记已读</li>
 *   <li>全部已读</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private User createUser(String usernamePrefix, Role role) {
        User u = new User();
        u.setUsername(usernamePrefix + "_" + System.nanoTime());
        u.setPassword("x");
        u.setPhone("138" + System.nanoTime());
        u.setRole(role);
        return userRepository.save(u);
    }

    private Notification seedNotification(Long userId, NotificationType type, boolean isRead) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setTitle("预约提醒");
        n.setContent("测试内容");
        n.setRelatedAppointmentId(System.nanoTime() & 0x7FFFFFFFL);
        n.setType(type);
        n.setIsRead(isRead);
        n.setCreatedAt(LocalDateTime.now());
        return notificationRepository.save(n);
    }

    /** 用例 1: USER 获取自己的通知 */
    @Test
    void userGetsOwnNotifications_succeeds() throws Exception {
        User alice = createUser("alice1", Role.USER);
        seedNotification(alice.getId(), NotificationType.APPOINTMENT_24H, false);
        seedNotification(alice.getId(), NotificationType.APPOINTMENT_1H, false);

        mockMvc.perform(get("/notifications").with(user(alice.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    /** 用例 2: USER 看不到其它用户通知 */
    @Test
    void userCannotSeeOthersNotifications_isolated() throws Exception {
        User alice = createUser("alice2", Role.USER);
        User bob = createUser("bob2", Role.USER);
        seedNotification(alice.getId(), NotificationType.APPOINTMENT_24H, false);
        seedNotification(bob.getId(), NotificationType.APPOINTMENT_24H, false);
        seedNotification(bob.getId(), NotificationType.APPOINTMENT_1H, false);

        // alice 只看到自己的 1 条
        mockMvc.perform(get("/notifications").with(user(alice.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(alice.getId()));

        // bob 只看到自己的 2 条
        mockMvc.perform(get("/notifications").with(user(bob.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    /** 用例 3: 未登录 GET /notifications → 401 */
    @Test
    void unauthenticatedList_returns401() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
    }

    /** 用例 4: unread count 正确 */
    @Test
    void unreadCount_isCorrect() throws Exception {
        User alice = createUser("alice4", Role.USER);
        seedNotification(alice.getId(), NotificationType.APPOINTMENT_24H, false);
        seedNotification(alice.getId(), NotificationType.APPOINTMENT_1H, false);
        seedNotification(alice.getId(), NotificationType.APPOINTMENT_24H, true); // 已读

        mockMvc.perform(get("/notifications/unread-count").with(user(alice.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    /** 用例 5: 单条已读 */
    @Test
    void markSingleRead_succeeds() throws Exception {
        User alice = createUser("alice5", Role.USER);
        Notification n = seedNotification(alice.getId(), NotificationType.APPOINTMENT_24H, false);

        mockMvc.perform(put("/notifications/" + n.getId() + "/read").with(user(alice.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(n.getId()))
                .andExpect(jsonPath("$.isRead").value(true));
    }

    /** 用例 6: USER 不能把其它用户通知标记已读 → 403 */
    @Test
    void cannotMarkOthersNotificationRead_returns403() throws Exception {
        User alice = createUser("alice6", Role.USER);
        User bob = createUser("bob6", Role.USER);
        Notification bobsNotification = seedNotification(bob.getId(), NotificationType.APPOINTMENT_24H, false);

        mockMvc.perform(put("/notifications/" + bobsNotification.getId() + "/read")
                        .with(user(alice.getUsername())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("只能标记自己的通知"));
    }

    /** 用例 7: 全部已读 */
    @Test
    void markAllRead_returnsCorrectCount() throws Exception {
        User alice = createUser("alice7", Role.USER);
        seedNotification(alice.getId(), NotificationType.APPOINTMENT_24H, false);
        seedNotification(alice.getId(), NotificationType.APPOINTMENT_1H, false);
        seedNotification(alice.getId(), NotificationType.APPOINTMENT_24H, true); // 已经已读

        mockMvc.perform(put("/notifications/read-all").with(user(alice.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        // 再次调用应返回 0
        mockMvc.perform(put("/notifications/read-all").with(user(alice.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(0));
    }
}