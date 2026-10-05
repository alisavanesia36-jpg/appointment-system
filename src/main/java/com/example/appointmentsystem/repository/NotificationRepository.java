package com.example.appointmentsystem.repository;

import com.example.appointmentsystem.entity.Notification;
import com.example.appointmentsystem.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * v2.2 第一阶段：站内通知 Repository。
 *
 * <p>继承 {@link JpaRepository}，所有方法均通过方法名派生，
 * 不写自定义 SQL，与本项目 {@link com.example.appointmentsystem.repository.AppointmentRepository}
 * 等既有 Repository 风格一致。
 *
 * <p>派生方法名约定的能力：
 * <ul>
 *   <li>findByUserIdOrderByCreatedAtDesc —— Spring Data 根据方法名自动生成 JPQL</li>
 *   <li>countByUserIdAndIsReadFalse —— 同上，返回 long</li>
 *   <li>existsByRelatedAppointmentIdAndType —— 同上，返回 boolean</li>
 *   <li>deleteByRelatedAppointmentId —— 同上，返回 int（已删除行数）</li>
 * </ul>
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 查询指定用户的所有通知，按 createdAt 倒序（最新在前）。
     * 通知列表页 / 通知角标刷新均基于此查询。
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 统计指定用户的未读通知数量。
     * 首页未读角标使用。
     */
    long countByUserIdAndIsReadFalse(Long userId);

    /**
     * 判断某预约在某通知类型下是否已经生成过通知。
     * 应用层幂等预检：与数据库 UNIQUE(related_appointment_id, type) 配合。
     */
    boolean existsByRelatedAppointmentIdAndType(
            Long relatedAppointmentId,
            NotificationType type
    );

    /**
     * 按关联预约 id 删除所有通知。
     * 改期 / 取消 / 管理员删除预约时联动清理使用。
     * 返回删除的行数。
     */
    int deleteByRelatedAppointmentId(Long relatedAppointmentId);
}