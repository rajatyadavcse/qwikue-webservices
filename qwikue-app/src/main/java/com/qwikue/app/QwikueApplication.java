package com.qwikue.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.microservice.LoginService",
    "com.restaurant.service",
    "com.kitchen.order",
    "com.qwikue.app"
})
@EntityScan(basePackages = {
    "com.microservice.LoginService.entity",
    "com.restaurant.service.dao",
    "com.kitchen.order.dao"
})
@EnableJpaRepositories(basePackages = {
    "com.microservice.LoginService.repository",
    "com.restaurant.service.repository",
    "com.kitchen.order.repository"
})
@EnableScheduling
public class QwikueApplication {
    public static void main(String[] args) {
        SpringApplication.run(QwikueApplication.class, args);
    }
}
