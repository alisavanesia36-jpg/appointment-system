package com.example.appointmentsystem.service;

import com.example.appointmentsystem.entity.Service;
import com.example.appointmentsystem.repository.ServiceRepository;


import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service
public class ServiceService {

    private final ServiceRepository serviceRepository;

    public ServiceService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public Service save(Service service) {
        return serviceRepository.save(service);
    }

    public List<Service> findAll() {
        return serviceRepository.findAll();
    }

    public Optional<Service> findById(Long id) {
        return serviceRepository.findById(id);
    }

    public void deleteById(Long id) {
        serviceRepository.deleteById(id);
    }
    public Service update(Long id, Service service) {
        Service existingService = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("服务不存在"));

        existingService.setName(service.getName());
        existingService.setDuration(service.getDuration());
        existingService.setPrice(service.getPrice());

        return serviceRepository.save(existingService);
    }
}