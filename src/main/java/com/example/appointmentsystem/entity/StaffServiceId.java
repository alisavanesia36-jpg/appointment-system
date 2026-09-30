package com.example.appointmentsystem.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StaffServiceId implements Serializable {

    private Long staffId;

    private Long serviceId;

    public StaffServiceId() {
    }

    public StaffServiceId(Long staffId, Long serviceId) {
        this.staffId = staffId;
        this.serviceId = serviceId;
    }

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof StaffServiceId)) {
            return false;
        }

        StaffServiceId that = (StaffServiceId) o;

        return Objects.equals(staffId, that.staffId)
                && Objects.equals(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(staffId, serviceId);
    }
}