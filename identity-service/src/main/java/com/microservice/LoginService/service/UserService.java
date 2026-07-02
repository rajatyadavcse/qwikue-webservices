package com.microservice.LoginService.service;

import com.microservice.LoginService.dto.*;
import com.microservice.LoginService.entity.Role;
import com.microservice.LoginService.entity.User;
import com.microservice.LoginService.exception.ApiException;
import com.microservice.LoginService.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService emailVerificationService;

    // ── Create User ───────────────────────────────────────────────────────────

    public UserResponse createUser(CreateUserRequest request, String createdByEmail) {
        // Email uniqueness check
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException("Email already in use: " + request.getEmail(), HttpStatus.CONFLICT);
        }

        User creator = userRepository.findByEmail(createdByEmail)
                .orElseThrow(() -> new ApiException("Creator not found", HttpStatus.NOT_FOUND));

        // ADMIN cannot create SUPER_ADMIN or another ADMIN
        if (creator.getRole() == Role.ADMIN) {
            if (request.getRole() == Role.SUPER_ADMIN || request.getRole() == Role.ADMIN) {
                throw new ApiException("ADMIN can only create STAFF, KITCHEN, or WAITER users", HttpStatus.FORBIDDEN);
            }
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .restaurantId(request.getRestaurantId())
                .phone(request.getPhone())
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        // Send OTP so the user can verify before logging in
        emailVerificationService.sendEmailVerificationOtp(savedUser);

        return UserResponse.from(savedUser);
    }

    // ── Get Users ─────────────────────────────────────────────────────────────

    public List<UserResponse> getUsers(Long restaurantId) {
        List<User> users = (restaurantId != null)
                ? userRepository.findByRestaurantId(restaurantId)
                : userRepository.findAll();
        return users.stream().map(UserResponse::from).collect(Collectors.toList());
    }

    // ── Get User By ID ────────────────────────────────────────────────────────

    public UserResponse getUserById(Long id) {
        return UserResponse.from(
                userRepository.findById(id)
                        .orElseThrow(() -> new ApiException("User not found with id: " + id, HttpStatus.NOT_FOUND)));
    }

    // ── Update User ───────────────────────────────────────────────────────────

    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException("User not found with id: " + id, HttpStatus.NOT_FOUND));

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getEmail() != null) {
            // Only check uniqueness if the email is actually changing
            if (!request.getEmail().equalsIgnoreCase(user.getEmail())
                    && userRepository.existsByEmail(request.getEmail())) {
                throw new ApiException("Email already in use: " + request.getEmail(), HttpStatus.CONFLICT);
            }
            user.setEmail(request.getEmail());
            // Re-trigger verification if email changed
            user.setIsEmailVerified(false);
        }
        if (request.getRestaurantId() != null) user.setRestaurantId(request.getRestaurantId());

        return UserResponse.from(userRepository.save(user));
    }

    // ── Delete User ───────────────────────────────────────────────────────────

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ApiException("User not found with id: " + id, HttpStatus.NOT_FOUND);
        }
        userRepository.deleteById(id);
    }

    // ── Activate / Deactivate ─────────────────────────────────────────────────

    public UserResponse updateStatus(Long id, UpdateStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException("User not found with id: " + id, HttpStatus.NOT_FOUND));
        user.setIsActive(request.getIsActive());
        return UserResponse.from(userRepository.save(user));
    }
}
