package com.microservice.LoginService.service;

/**
 * Internal record carrying all three token-related values returned by AuthService.
 * The controller uses refreshToken to set the HttpOnly cookie and exposes only
 * accessToken + role in the response body.
 */
public record AuthTokens(String accessToken, String refreshToken, String role) {}
