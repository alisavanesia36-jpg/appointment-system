package com.example.appointmentsystem.dto;

import lombok.Data;

@Data
public class UserCreateDTO {

    private String username;

    private String password;

    private String phone;
}
