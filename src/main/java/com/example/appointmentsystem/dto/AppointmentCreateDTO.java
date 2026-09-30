package com.example.appointmentsystem.dto;


import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;


@Data
public class AppointmentCreateDTO {


    @NotNull(message = "用户不能为空")
    private Long userId;


    @NotNull(message = "服务不能为空")
    private Long serviceId;


    @NotNull(message = "员工不能为空")
    private Long staffId;


    @NotNull(message = "预约时间不能为空")
    private LocalDateTime appointmentTime;


}