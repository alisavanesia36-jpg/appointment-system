package com.example.appointmentsystem.service;

import com.example.appointmentsystem.entity.StaffServiceId;
import com.example.appointmentsystem.entity.StaffServiceMapping;
import com.example.appointmentsystem.repository.StaffServiceMappingRepository;

import java.util.List;

@org.springframework.stereotype.Service
public class StaffServiceMappingService {

    private final StaffServiceMappingRepository repository;

    public StaffServiceMappingService(
            StaffServiceMappingRepository repository) {
        this.repository = repository;
    }

    // 添加员工技能
    public StaffServiceMapping save(
            Long staffId,
            Long serviceId) {

        StaffServiceId id =
                new StaffServiceId(staffId, serviceId);

        StaffServiceMapping mapping =
                new StaffServiceMapping(id);

        return repository.save(mapping);
    }

    // 查询所有员工技能关系
    public List<StaffServiceMapping> findAll() {
        return repository.findAll();
    }
    // 删除员工技能
    public void delete(
            Long staffId,
            Long serviceId) {

        StaffServiceId id =
                new StaffServiceId(staffId, serviceId);

        repository.deleteById(id);
    }
    // 查询某个员工会哪些服务
    public List<StaffServiceMapping> findByStaffId(Long staffId) {
        return repository.findByIdStaffId(staffId);
    }
    // 查询某个服务有哪些员工可以做
    public List<StaffServiceMapping> findByServiceId(Long serviceId) {
        return repository.findByIdServiceId(serviceId);
    }
    public boolean exists(Long staffId, Long serviceId) {
        return repository.existsByIdStaffIdAndIdServiceId(
                staffId,
                serviceId
        );
    }
}