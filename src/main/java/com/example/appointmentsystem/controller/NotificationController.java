package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.Notification;
import com.example.appointmentsystem.security.SecurityUtils;
import com.example.appointmentsystem.service.NotificationService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * v2.2 第一阶段：站内通知 Controller。
 *
 * <p>所有端点仅作用于当前登录用户（{@link SecurityUtils#getCurrentUsername()}）。
 * 不接受 userId 路径/查询参数，杜绝越权。
 *
 * <p>权限：依赖 SecurityConfig 的 {@code anyRequest().authenticated()}，
 * 本 Controller 不修改 SecurityConfig。
 *
 * <p>端点：
 * <ul>
 *   <li>GET    /notifications              当前用户通知列表（倒序）</li>
 *   <li>GET    /notifications/unread-count 当前用户未读数量</li>
 *   <li>PUT    /notifications/{id}/read    标记单条已读（仅本人）</li>
 *   <li>PUT    /notifications/read-all     当前用户全部已读</li>
 * </ul>
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 当前登录用户的全部通知，按 createdAt 倒序。
     * 不接受 userId 参数。
     */
    @GetMapping
    public List<Notification> list() {
        return notificationService.listMine(
                SecurityUtils.getCurrentUsername()
        );
    }

    /**
     * 当前登录用户的未读通知数量。
     */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        long count = notificationService.countUnread(
                SecurityUtils.getCurrentUsername()
        );
        return Map.of("count", count);
    }

    /**
     * 标记单条通知已读。
     * 仅本人可标记 —— 非本人 403；幂等不抛 404。
     */
    @PutMapping("/{id}/read")
    public Notification markRead(@PathVariable Long id) {
        return notificationService.markRead(
                id,
                SecurityUtils.getCurrentUsername()
        );
    }

    /**
     * 当前登录用户全部已读。
     * 返回实际修改行数。
     */
    @PutMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        int updated = notificationService.markAllRead(
                SecurityUtils.getCurrentUsername()
        );
        return Map.of("updated", updated);
    }
}