package com.microservice.LoginService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for login and refresh endpoints.
 * The refresh token is intentionally absent — it is stored in an HttpOnly cookie
 * and never exposed to JavaScript.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String role;
}
