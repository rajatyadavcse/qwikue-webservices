package com.microservice.LoginService.dto;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private Long restaurantId;
}
