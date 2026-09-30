package com.example.appointmentsystem.repository;

import com.example.appointmentsystem.entity.StaffServiceId;
import com.example.appointmentsystem.entity.StaffServiceMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffServiceMappingRepository
        extends JpaRepository<StaffServiceMapping, StaffServiceId> {

    List<StaffServiceMapping> findByIdStaffId(Long staffId);

    List<StaffServiceMapping> findByIdServiceId(Long serviceId);

    boolean existsByIdStaffIdAndIdServiceId(
            Long staffId,
            Long serviceId
    );
}