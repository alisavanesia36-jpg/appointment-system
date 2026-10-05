package com.example.appointmentsystem.service;

import com.example.appointmentsystem.entity.Staff;
import com.example.appointmentsystem.exception.BusinessException;
import com.example.appointmentsystem.repository.StaffRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 删除员工。
     *
     * 修复说明（v1.7.2 bug fix）：
     * 当员工已存在 staff_services 或 appointments 关联记录时，
     * 数据库外键约束会抛出 DataIntegrityViolationException。
     * 如果不显式处理，异常会在事务 commit 阶段才被抛出，
     * 进而被 Spring Security 错误链路误转为 401 "未认证或 token 无效"，
     * 导致前端 request.js 误清 token 并跳登录页。
     *
     * 解决方案：
     * 1. @Transactional 明确事务边界，使 SQL 在本方法内执行；
     * 2. existsById 提前校验避免 EmptyResultDataAccessException 干扰；
     * 3. 显式 flush() 强制让 Hibernate 立刻执行 SQL，
     *    使外键违反错误在 try 范围内确定发生；
     * 4. 捕获 DataIntegrityViolationException 转为 BusinessException，
     *    由已有的 GlobalExceptionHandler 输出 400 + {"message":"..."}，
     *    不需要修改 GlobalExceptionHandler。
     */
    @Transactional
    public void deleteById(Long id) {
        if (id == null) {
            throw new BusinessException("员工ID不能为空");
        }
        if (!staffRepository.existsById(id)) {
            throw new BusinessException("员工不存在");
        }
        try {
            staffRepository.deleteById(id);
            // 强制 flush，让外键约束异常在此 try 块内确定抛出，
            // 否则异常会延迟到事务 commit 阶段。
            staffRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("该员工存在预约或服务分配记录，无法删除");
        }
    }
}