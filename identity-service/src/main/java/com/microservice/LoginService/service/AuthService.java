package com.microservice.LoginService.service;

import com.microservice.LoginService.dto.*;
import com.microservice.LoginService.entity.User;
import com.microservice.LoginService.exception.ApiException;
import com.microservice.LoginService.repository.UserRepository;
import com.microservice.LoginService.security.JwtUtil;
import com.microservice.LoginService.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // In-memory store: email → refresh token (lost on restart)
    private final Map<String, String> refreshTokenStore = new ConcurrentHashMap<>();

    @Autowired
    private EmailVerificationService emailVerificationService;

    // ── Login ─────────────────────────────────────────────────────────────────

    public AuthTokens login(LoginRequest request) {
        // Spring Security calls loadUserByUsername(email) internally
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        User user = principal.getUser();

        // Block login if email is not yet verified
        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new ApiException(
                    "Email not verified. Please check your inbox for the verification OTP.",
                    HttpStatus.FORBIDDEN);
        }

        String accessToken  = jwtUtil.generateAccessToken(user.getEmail(), user.getRole(), user.getRestaurantId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        refreshTokenStore.put(user.getEmail(), refreshToken);

        return new AuthTokens(accessToken, refreshToken, "ROLE_" + user.getRole().name());
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    public AuthTokens refreshToken(String refreshToken) {
        String email;
        try {
            email = jwtUtil.extractUsername(refreshToken);
        } catch (Exception e) {
            throw new ApiException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }

        if (jwtUtil.isTokenExpired(refreshToken)) {
            refreshTokenStore.remove(email);
            throw new ApiException("Refresh token expired. Please login again.", HttpStatus.UNAUTHORIZED);
        }

        String storedToken = refreshTokenStore.get(email);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new ApiException("Refresh token is invalid or already used", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        String newAccessToken  = jwtUtil.generateAccessToken(user.getEmail(), user.getRole(), user.getRestaurantId());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        refreshTokenStore.put(email, newRefreshToken);

        return new AuthTokens(newAccessToken, newRefreshToken, "ROLE_" + user.getRole().name());
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    public void logout(String email) {
        refreshTokenStore.remove(email);
    }

    // ── Reset Password (Admin-only) ───────────────────────────────────────────

    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException("User not found: " + request.getEmail(), HttpStatus.NOT_FOUND));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenStore.remove(user.getEmail());
    }

    // ── Change Password (Self-service) ────────────────────────────────────────

    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new ApiException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenStore.remove(email);
    }

    // ── Verify Email ──────────────────────────────────────────────────────────

    public void verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new ApiException("Email is already verified.", HttpStatus.BAD_REQUEST);
        }

        emailVerificationService.verifyEmailOtp(user, request.getOtp());

        user.setIsEmailVerified(true);
        userRepository.save(user);
        log.info("AuthService: email verified for '{}'", user.getEmail());
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
                log.warn("AuthService: forgot-password requested for unverified email '{}'", request.getEmail());
                return;
            }
            emailVerificationService.sendPasswordResetOtp(user);
        });
    }

    // ── Resend Verification OTP ───────────────────────────────────────────────

    /**
     * Resends the email verification OTP for an unverified account.
     * <p>
     * Unlike the forgot-password flow, this endpoint returns explicit errors for
     * unknown emails and already-verified accounts. This is intentional:
     * the registration endpoint already reveals whether an email is taken, so
     * hiding email existence here is inconsistent and actively harms UX by leaving
     * users with no actionable feedback when they mistype their email at registration.
     */
    public void resendVerificationOtp(ResendVerificationOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException(
                        "No account found with this email. Please register first.",
                        HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new ApiException(
                    "This email is already verified. You can log in directly.",
                    HttpStatus.BAD_REQUEST);
        }

        emailVerificationService.resendEmailVerificationOtp(user);
    }

    // ── Reset Password with OTP ────────────────────────────────────────────────

    public void resetPasswordWithOtp(ResetPasswordWithOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException(
                        "No account found with this email address.", HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new ApiException(
                    "Email is not verified. Password reset is not available for this account.",
                    HttpStatus.FORBIDDEN);
        }

        emailVerificationService.verifyPasswordResetOtp(user, request.getOtp());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate all active sessions for this user
        refreshTokenStore.remove(user.getEmail());
        log.info("AuthService: password reset via OTP for '{}'", user.getEmail());
    }
}
