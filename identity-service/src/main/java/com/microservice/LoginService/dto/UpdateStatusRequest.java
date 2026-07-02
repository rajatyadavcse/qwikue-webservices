package com.microservice.LoginService.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotNull(message = "isActive flag is required")
    private Boolean isActive;
}
