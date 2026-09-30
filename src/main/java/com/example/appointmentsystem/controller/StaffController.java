package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.Staff;
import com.example.appointmentsystem.service.StaffService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    // 新增员工
    @PostMapping
    public Staff create(@RequestBody Staff staff) {
        return staffService.save(staff);
    }

    // 查询所有员工
    @GetMapping
    public List<Staff> findAll() {
        return staffService.findAll();
    }

    // 根据ID查询员工
    @GetMapping("/{id}")
    public Staff findById(@PathVariable Long id) {
        return staffService.findById(id)
                .orElse(null);
    }

    // 删除员工
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        staffService.deleteById(id);
        return "删除成功";
    }
}