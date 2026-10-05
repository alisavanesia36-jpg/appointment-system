package com.example.appointmentsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v2.2 第一阶段：站内通知实体。
 *
 * <p>字段：
 * <ul>
 *   <li>id —— 主键，自增</li>
 *   <li>userId —— 接收人（数据隔离依据）</li>
 *   <li>title —— 通知标题</li>
 *   <li>content —— 通知正文</li>
 *   <li>type —— {@link NotificationType}，{@code @Enumerated(STRING)}</li>
 *   <li>relatedAppointmentId —— 关联预约 id</li>
 *   <li>isRead —— 是否已读（默认 false）</li>
 *   <li>createdAt —— 通知创建时间（{@code @PrePersist} 填充）</li>
 * </ul>
 *
 * <p>幂等保证：数据库层 {@code UNIQUE(related_appointment_id, type)} 唯一约束。
 * Hibernate 在 {@code ddl-auto=update} 模式会发出 {@code ALTER TABLE ... ADD CONSTRAINT}；
 * 在 {@code ddl-auto=create-drop} 模式会在 CREATE TABLE 中包含 UNIQUE 子句。
 */
@Entity
@Table(
    name = "notifications",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_notifications_appt_type",
        columnNames = {"related_appointment_id", "type"}
    )
)
@Data
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收人 userId —— 列表查询 / 未读数量 / 数据隔离均基于此字段 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 通知标题，例如"预约提醒" */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /** 通知正文，含服务名 / 员工名 / 时间 / 倒计时 */
    @Column(name = "content", nullable = false, length = 500)
    private String content;

    /**
     * 通知类型。
     *
     * <p>{@code @Enumerated(EnumType.STRING)} 保证数据库写入形如
     * {@code "APPOINTMENT_24H"} / {@code "APPOINTMENT_1H"}，绝不入库 ordinal 数字。
     * 与 {@code Appointment.status}（见 Appointment.java:28）写入策略一致。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    /** 关联预约 id，用于幂等去重 + 改期/取消/删除时清理 + 未来点击跳转 */
    @Column(name = "related_appointment_id", nullable = false)
    private Long relatedAppointmentId;

    /** 是否已读；新建 unread;未读角标依据此字段 */
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    /**
     * 创建时间。{@code @PrePersist} 在 INSERT 之前自动写入当前时刻，
     * 不依赖前端传值；测试场景下与 {@code LocalDateTime.now(clock)} 一致语义。
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.isRead == null) {
            this.isRead = false;
        }
    }
}