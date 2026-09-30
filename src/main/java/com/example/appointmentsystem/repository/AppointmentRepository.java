package com.example.appointmentsystem.repository;

import com.example.appointmentsystem.entity.Appointment;
import com.example.appointmentsystem.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    boolean existsByAppointmentTimeAndStatusNot(
            LocalDateTime appointmentTime,
            AppointmentStatus status
    );

    boolean existsByStaffIdAndAppointmentTimeAndStatusNot(
            Long staffId,
            LocalDateTime appointmentTime,
            AppointmentStatus status
    );

    // 查询某个员工所有未取消的预约
    List<Appointment> findByStaffIdAndStatusNot(
            Long staffId,
            AppointmentStatus status
    );

    List<Appointment> findByUserId(Long userId);

    List<Appointment> findByUserIdAndStatusIn(
            Long userId,
            List<AppointmentStatus> statuses
    );

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByAppointmentTimeBetween(
            LocalDateTime start,
            LocalDateTime end
    );
}