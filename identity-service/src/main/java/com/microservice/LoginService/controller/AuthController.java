package com.microservice.LoginService.controller;

import com.microservice.LoginService.dto.*;
import com.microservice.LoginService.entity.User;
import com.microservice.LoginService.exception.ApiException;
import com.microservice.LoginService.security.UserPrincipal;
import com.microservice.LoginService.service.AuthService;
import com.microservice.LoginService.service.AuthTokens;
import com.microservice.LoginService.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Login, token management, and password operations")
public class AuthController {

    @Autowired
    private AuthService authService;

    /** Matches jwt.refresh-token-expiry-ms so the cookie lifetime mirrors the token lifetime. */
    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    /**
     * Set COOKIE_SECURE=false in local dev (HTTP) and COOKIE_SECURE=true in production (HTTPS/Railway).
     * Defaults to true for a safe production posture.
     */
    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    /**
     * SameSite policy for the refresh-token cookie.
     *   Strict  – most secure; cookie not sent from any cross-site navigation (default)
     *   Lax     – sent on top-level navigation GETs; safe middle-ground
     *   None    – sent cross-origin (requires Secure=true; needed for cross-origin SPAs)
     * Set via COOKIE_SAME_SITE env var on Railway.
     */
    @Value("${app.cookie.same-site:Strict}")
    private String cookieSameSite;

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Login with email and password",
            description = "Returns an access token in the response body and sets a secure HttpOnly " +
                          "refresh-token cookie. Never expose the cookie value to client-side JavaScript.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        AuthTokens tokens = authService.login(request);

        // Write refresh token as HttpOnly cookie — JS cannot read it
        ResponseCookie cookie = CookieUtil.buildRefreshCookie(
                tokens.refreshToken(),
                refreshTokenExpiryMs / 1000,
                cookieSecure,
                cookieSameSite);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(LoginResponse.builder()
                .accessToken(tokens.accessToken())
                .role(tokens.role())
                .build());
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Obtain a new access token using the HttpOnly refresh-token cookie",
            description = "No request body required. The browser automatically sends the " +
                          "refresh_token cookie. Both the access token and the refresh cookie are rotated.")
    public ResponseEntity<LoginResponse> refresh(
            @Parameter(hidden = true) HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = extractRefreshCookie(request);

        AuthTokens tokens = authService.refreshToken(refreshToken);

        // Rotate: issue a new cookie with the rotated refresh token
        ResponseCookie cookie = CookieUtil.buildRefreshCookie(
                tokens.refreshToken(),
                refreshTokenExpiryMs / 1000,
                cookieSecure,
                cookieSameSite);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(LoginResponse.builder()
                .accessToken(tokens.accessToken())
                .role(tokens.role())
                .build());
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Logout — invalidates refresh token and clears the cookie",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MessageResponse> logout(@AuthenticationPrincipal UserPrincipal principal,
                                                  HttpServletResponse response) {
        authService.logout(principal.getUsername());

        // Expire the cookie immediately
        ResponseCookie cleared = CookieUtil.clearRefreshCookie(cookieSecure, cookieSameSite);
        response.addHeader(HttpHeaders.SET_COOKIE, cleared.toString());

        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    // ── Me ────────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user info",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = principal.getUser();
        return ResponseEntity.ok(MeResponse.from(user));
    }

    // ── Reset Password (Admin-only) ───────────────────────────────────────────

    @PostMapping("/reset-password")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Reset another user's password (ADMIN / SUPER_ADMIN only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Password reset successfully"));
    }

    // ── Change Password (Self-service) ────────────────────────────────────────

    @PatchMapping("/change-password")
    @Operation(summary = "Change own password (any authenticated user)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getUsername(), request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully"));
    }

    // ── Verify Email ──────────────────────────────────────────────────────

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address using the OTP sent at registration",
            description = "No authentication required. Submit the 6-digit OTP sent to the user's registered email.")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully. You can now log in."));
    }

    // ── Resend Verification OTP ───────────────────────────────────────────────

    @PostMapping("/resend-verification-otp")
    @Operation(summary = "Resend the email-verification OTP",
            description = "No authentication required. Use this when the original OTP was not received or has expired. " +
                          "Returns 404 if the email is not registered, 400 if already verified, and 429 if rate-limited " +
                          "(max 3 requests per hour per email).")
    public ResponseEntity<MessageResponse> resendVerificationOtp(
            @Valid @RequestBody ResendVerificationOtpRequest request) {
        authService.resendVerificationOtp(request);
        return ResponseEntity.ok(new MessageResponse(
                "A new verification OTP has been sent to your email."));
    }

    // ── Forgot Password ──────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password-reset OTP",
            description = "No authentication required. A 6-digit OTP is sent to the registered email if it exists " +
                          "and is verified. Always returns 200 to prevent email enumeration.")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(new MessageResponse(
                "If this email is registered and verified, an OTP has been sent."));
    }

    // ── Reset Password with OTP ────────────────────────────────────────────

    @PostMapping("/reset-password-otp")
    @Operation(summary = "Reset password using OTP (self-service forgot-password flow)",
            description = "No authentication required. Submit the OTP received via email along with the new password.")
    public ResponseEntity<MessageResponse> resetPasswordWithOtp(
            @Valid @RequestBody ResetPasswordWithOtpRequest request) {
        authService.resetPasswordWithOtp(request);
        return ResponseEntity.ok(new MessageResponse("Password reset successfully. You can now log in."));
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private String extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return Optional.ofNullable(cookies)
                .flatMap(cs -> Arrays.stream(cs)
                        .filter(c -> CookieUtil.REFRESH_COOKIE_NAME.equals(c.getName()))
                        .map(Cookie::getValue)
                        .findFirst())
                .orElseThrow(() -> new ApiException(
                        "Refresh token cookie is missing. Please login again.", HttpStatus.UNAUTHORIZED));
    }
}
