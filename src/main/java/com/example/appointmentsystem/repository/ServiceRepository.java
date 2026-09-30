package com.example.appointmentsystem.repository;

import com.example.appointmentsystem.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<Service, Long> {
}