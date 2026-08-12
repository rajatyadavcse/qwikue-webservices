package com.microservice.LoginService.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Identifier (email or phone number) is required")
    @Pattern(
            regexp = "^([a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+|[0-9]{10})$",
            message = "Must be a valid email address or a 10-digit phone number"
    )
    @JsonAlias({"userId", "email", "phone", "username", "phoneNumber"})
    private String identifier;

    @NotBlank(message = "Password is required")
    private String password;
}
