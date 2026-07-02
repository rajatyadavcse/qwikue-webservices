package com.microservice.LoginService.util;

import org.springframework.http.ResponseCookie;

/**
 * Utility for building HttpOnly refresh-token cookies.
 *
 * Cookie flags used:
 *   HttpOnly  – JavaScript cannot access the token (XSS protection)
 *   Secure    – sent only over HTTPS (set via COOKIE_SECURE env var; false locally)
 *   SameSite  – controls cross-site sending (default: Strict; use None for cross-origin SPAs)
 *   Path=/auth – cookie is only attached to /auth/* requests, nowhere else
 */
public final class CookieUtil {

    public static final String REFRESH_COOKIE_NAME = "refresh_token";

    private CookieUtil() {}

    /** Build a cookie that carries a valid refresh token. */
    public static ResponseCookie buildRefreshCookie(String token,
                                                    long maxAgeSeconds,
                                                    boolean secure,
                                                    String sameSite) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .path("/auth")
                .maxAge(maxAgeSeconds)
                .sameSite(sameSite)
                .build();
    }

    /** Build an expired / empty cookie to clear the refresh token from the browser. */
    public static ResponseCookie clearRefreshCookie(boolean secure, String sameSite) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .path("/auth")
                .maxAge(0)
                .sameSite(sameSite)
                .build();
    }
}
