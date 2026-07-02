package com.microservice.LoginService.repository;

import com.microservice.LoginService.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByRestaurantId(Long restaurantId);
    boolean existsByEmail(String email);
}
