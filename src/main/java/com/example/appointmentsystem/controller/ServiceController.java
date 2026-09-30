package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.entity.Service;
import com.example.appointmentsystem.service.ServiceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/services")
public class ServiceController {

    private final ServiceService serviceService;

    public ServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @PostMapping
    public Service create(@RequestBody Service service) {
        return serviceService.save(service);
    }

    @GetMapping
    public List<Service> findAll() {
        return serviceService.findAll();
    }

    @GetMapping("/{id}")
    public Service findById(@PathVariable Long id) {
        return serviceService.findById(id)
                .orElse(null);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        serviceService.deleteById(id);
        return "删除成功";
    }
    @PutMapping("/{id}")
    public Service update(
            @PathVariable Long id,
            @RequestBody Service service) {
        return serviceService.update(id, service);
    }
}