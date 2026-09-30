package com.example.appointmentsystem.service;

import com.example.appointmentsystem.entity.Staff;
import com.example.appointmentsystem.repository.StaffRepository;

import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service
public class StaffService {

    private final StaffRepository staffRepository;

    public StaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    // 新增员工
    public Staff save(Staff staff) {
        return staffRepository.save(staff);
    }

    // 查询所有员工
    public List<Staff> findAll() {
        return staffRepository.findAll();
    }

    // 根据ID查询员工
    public Optional<Staff> findById(Long id) {
        return staffRepository.findById(id);
    }

    // 删除员工
    public void deleteById(Long id) {
        staffRepository.deleteById(id);
    }
}