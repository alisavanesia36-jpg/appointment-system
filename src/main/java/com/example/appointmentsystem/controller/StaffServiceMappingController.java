package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.StaffServiceMapping;
import com.example.appointmentsystem.service.StaffServiceMappingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/staff-services")
public class StaffServiceMappingController {

    private final StaffServiceMappingService service;

    public StaffServiceMappingController(
            StaffServiceMappingService service) {
        this.service = service;
    }

    // 添加员工技能
    @PostMapping("/{staffId}/{serviceId}")
    public StaffServiceMapping add(
            @PathVariable Long staffId,
            @PathVariable Long serviceId) {

        return service.save(staffId, serviceId);
    }

    // 查询所有员工技能
    @GetMapping
    public List<StaffServiceMapping> findAll() {
        return service.findAll();
    }

    // 查询某个员工会哪些服务
    @GetMapping("/staff/{staffId}")
    public List<StaffServiceMapping> findByStaffId(
            @PathVariable Long staffId) {

        return service.findByStaffId(staffId);
    }
    // 查询某个服务有哪些员工可以做
    @GetMapping("/service/{serviceId}")
    public List<StaffServiceMapping> findByServiceId(
            @PathVariable Long serviceId) {

        return service.findByServiceId(serviceId);
    }

    // 删除员工技能
    @DeleteMapping("/{staffId}/{serviceId}")
    public String delete(
            @PathVariable Long staffId,
            @PathVariable Long serviceId) {

        service.delete(staffId, serviceId);

        return "删除成功";
    }
}