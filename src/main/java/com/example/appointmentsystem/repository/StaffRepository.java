package com.example.appointmentsystem.repository;

import com.example.appointmentsystem.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, Long> {
}