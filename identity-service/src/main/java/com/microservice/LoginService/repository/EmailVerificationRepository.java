package com.microservice.LoginService.repository;

import com.microservice.LoginService.entity.EmailVerification;
import com.microservice.LoginService.entity.EmailVerification.OtpType;
import com.microservice.LoginService.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /**
     * Find the latest unused, non-expired OTP for a user and type.
     * Only one active OTP per user per type should exist at a time.
     */
    Optional<EmailVerification> findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(User user, OtpType type);

    /**
     * Invalidate all previous pending OTPs for the same user and type before issuing a new one.
     */
    @Modifying
    @Transactional
    @Query("UPDATE EmailVerification e SET e.used = true WHERE e.user = :user AND e.type = :type AND e.used = false")
    void invalidateAllPendingByUserAndType(@Param("user") User user, @Param("type") OtpType type);

    /**
     * Count how many OTPs of a given type were issued for a user after {@code since}.
     * Used for rate-limiting resend requests (e.g. max N requests per hour).
     */
    @Query("SELECT COUNT(e) FROM EmailVerification e WHERE e.user = :user AND e.type = :type AND e.createdAt >= :since")
    long countByUserAndTypeAndCreatedAtAfter(@Param("user") User user,
                                             @Param("type") OtpType type,
                                             @Param("since") LocalDateTime since);
}
