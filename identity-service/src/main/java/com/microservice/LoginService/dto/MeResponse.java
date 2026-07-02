package com.microservice.LoginService.dto;

import com.microservice.LoginService.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private Long restaurantId;

    public static MeResponse from(User user) {
        return MeResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role("ROLE_" + user.getRole().name())
                .restaurantId(user.getRestaurantId())
                .build();
    }
}
