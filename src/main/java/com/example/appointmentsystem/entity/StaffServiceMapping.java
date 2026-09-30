package com.example.appointmentsystem.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "staff_services")
public class StaffServiceMapping {

    @EmbeddedId
    private StaffServiceId id;

    public StaffServiceMapping() {
    }

    public StaffServiceMapping(StaffServiceId id) {
        this.id = id;
    }

    public StaffServiceId getId() {
        return id;
    }

    public void setId(StaffServiceId id) {
        this.id = id;
    }
}