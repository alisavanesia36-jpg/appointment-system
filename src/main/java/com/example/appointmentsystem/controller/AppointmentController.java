package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.Appointment;
import com.example.appointmentsystem.entity.AppointmentStatus;
import com.example.appointmentsystem.security.SecurityUtils;
import com.example.appointmentsystem.service.AppointmentService;


import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;




@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(
            AppointmentService appointmentService) {

        this.appointmentService = appointmentService;
    }


    // 创建预约
    @PostMapping
    public Appointment create(
            @RequestBody Appointment appointment) {

        return appointmentService.save(
                appointment,
                SecurityUtils.getCurrentUsername()
        );
    }


    // 查询全部预约
    @GetMapping
    public List<Appointment> findAll() {

        return appointmentService.findAll();
    }


    // 查询用户全部预约
    @GetMapping("/user/{userId}")
    public List<Appointment> findByUserId(
            @PathVariable Long userId) {

        return appointmentService.findByUserId(
                userId,
                SecurityUtils.getCurrentUsername()
        );
    }


    // 查询当前登录用户的预约
    @GetMapping("/my")
    public List<Appointment> findMyAppointments() {

        return appointmentService.findMyAppointments(
                SecurityUtils.getCurrentUsername()
        );
    }


    // 查询用户未完成预约
    @GetMapping("/user/{userId}/unfinished")
    public List<Appointment> findUnfinishedByUserId(
            @PathVariable Long userId) {

        return appointmentService.findUnfinishedByUserId(
                userId,
                SecurityUtils.getCurrentUsername()
        );
    }


    // 根据状态查询
    @GetMapping("/status/{status}")
    public List<Appointment> findByStatus(
            @PathVariable AppointmentStatus status) {

        return appointmentService.findByStatus(status);
    }


    // 根据时间范围查询
    @GetMapping("/time-range")
    public List<Appointment> findByAppointmentTimeBetween(
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) {

        return appointmentService.findByAppointmentTimeBetween(
                start,
                end
        );
    }


    // 根据ID查询
    @GetMapping("/{id}")
    public Appointment findById(
            @PathVariable Long id) {

        return appointmentService.findById(
                id,
                SecurityUtils.getCurrentUsername()
        );
    }


    // 删除预约
    @DeleteMapping("/{id}")
    public String delete(
            @PathVariable Long id) {

        appointmentService.deleteById(id);

        return "删除成功";
    }


    // 修改预约
    @PutMapping("/{id}")
    public Appointment update(
            @PathVariable Long id,
            @RequestBody Appointment appointment) {

        return appointmentService.update(
                id,
                appointment,
                SecurityUtils.getCurrentUsername()
        );
    }


    // 确认预约
    @PutMapping("/{id}/confirm")
    public Appointment confirm(
            @PathVariable Long id) {

        return appointmentService.confirm(id);
    }


    // 取消预约
    @PutMapping("/{id}/cancel")
    public Appointment cancel(
            @PathVariable Long id) {

        return appointmentService.cancel(
                id,
                SecurityUtils.getCurrentUsername()
        );
    }


    // 完成预约
    @PutMapping("/{id}/complete")
    public Appointment complete(
            @PathVariable Long id) {

        return appointmentService.complete(id);
    }

    // ==================== v2.0 第一阶段：可用时间段查询 ====================
    // GET /appointments/available-slots?staffId=X&serviceId=Y&date=YYYY-MM-DD
    // 任何登录用户可访问（沿用 SecurityConfig anyRequest().authenticated()）
    // 返回 List<String>，例如 ["09:00","10:00","11:00",...]
    @GetMapping("/available-slots")
    public List<String> getAvailableSlots(
            @RequestParam Long staffId,
            @RequestParam Long serviceId,
            @RequestParam String date
    ) {
        return appointmentService.findAvailableSlots(
                staffId, serviceId, date);
    }
}
