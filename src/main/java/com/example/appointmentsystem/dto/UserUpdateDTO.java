package com.example.appointmentsystem.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserUpdateDTO {

    private String username;

    private String password;

    private String phone;
}
