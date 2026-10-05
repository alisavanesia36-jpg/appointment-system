package com.example.appointmentsystem.entity;

/**
 * v2.2 第一阶段：站内通知类型枚举。
 *
 * 仅做两类时间提醒：预约前 24 小时 / 预约前 1 小时。
 *
 * 数据库保存策略：{@code @Enumerated(EnumType.STRING)}，
 * 实际写入形如 "APPOINTMENT_24H"，绝不写入 ordinal 数字。
 * 与 Appointment.status 使用相同的写入策略（见 Appointment.java:28）。
 */
public enum NotificationType {

    /** 预约前 24 小时提醒 */
    APPOINTMENT_24H,

    /** 预约前 1 小时提醒 */
    APPOINTMENT_1H
}