package com.example.appointmentsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// v2.2 第一阶段：开启 Spring 自带的 @Scheduled 支持
@EnableScheduling
public class AppointmentSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointmentSystemApplication.class, args);
    }

}
