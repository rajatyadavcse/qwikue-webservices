package com.microservice.LoginService.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private String firstName;
    private String lastName;

    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
    private String phone;

    private String email;
    private Long restaurantId;
}
