package com.example.appointmentsystem.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.example.appointmentsystem.entity.Role;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserAdminUpdateDTO {

    private String username;

    private String password;

    private String phone;

    private Role role;
}
