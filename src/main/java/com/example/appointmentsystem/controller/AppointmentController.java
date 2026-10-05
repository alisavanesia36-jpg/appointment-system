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
    // GET /appointments/available-slots?staffId=X&serviceId=Y&date=YYYY-MM-DD[&excludeAppointmentId=Z]
    // 任何登录用户可访问（沿用 SecurityConfig anyRequest().authenticated()）
    // 返回 List<String>，例如 ["09:00","10:00","11:00",...]
    // excludeAppointmentId 是 v2.1 第一阶段新增的可选参数，用于改期场景：
    //   不传：v2.0 行为完全不变（向后兼容）
    //   传值：从 busy 列表中剔除该预约自身，让原时段可重新出现在可用时间中
    //   必须保证该预约的 staffId / serviceId 与请求参数匹配，否则返回 400 "预约不存在或不匹配"
    @GetMapping("/available-slots")
    public List<String> getAvailableSlots(
            @RequestParam Long staffId,
            @RequestParam Long serviceId,
            @RequestParam String date,
            @RequestParam(required = false) Long excludeAppointmentId
    ) {
        return appointmentService.findAvailableSlots(
                staffId, serviceId, date, excludeAppointmentId);
    }

    // ==================== v2.1 第一阶段：预约改期 ====================
    // PUT /appointments/{id}/reschedule
    // 请求体：{ "appointmentTime": "YYYY-MM-DDTHH:mm:ss" }
    // 业务约束：
    //   - 仅允许修改 appointmentTime
    //   - serviceId / staffId / userId / status 一律从数据库原值覆盖入参（忽略前端传值）
    //   - 仅 PENDING / CONFIRMED 可被改期
    //   - USER 只能改自己的预约，ADMIN 可以改任何人
    //   - 所有预约业务校验（员工-服务关系、营业时间、当前时间、冲突）由 Service 层 reschedule() 完整执行
    @PutMapping("/{id}/reschedule")
    public Appointment reschedule(
            @PathVariable Long id,
            @RequestBody Appointment appointment) {

        return appointmentService.reschedule(
                id,
                appointment,
                SecurityUtils.getCurrentUsername()
        );
    }
}
