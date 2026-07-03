package com.qwikue.app.scheduler;
 
import com.microservice.LoginService.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
 
/**
 * Consolidated keep-alive database ping scheduler at application level.
 * Only runs if "app.keep-alive.enabled" is set to "true" (disabled by default).
 */
@Component
@ConditionalOnProperty(name = "app.keep-alive.enabled", havingValue = "true")
public class KeepAliveScheduler {
 
    private static final Logger log = LoggerFactory.getLogger(KeepAliveScheduler.class);
    private final UserRepository userRepository;
 
    public KeepAliveScheduler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
 
    @Scheduled(cron = "${app.keep-alive.cron:0 0/10 * * * *}")
    public void keepDatabaseConnectionAlive() {
        try {
            long count = userRepository.count();
            log.info("[KeepAlive] Application level DB ping successful — total users in DB: {}", count);
        } catch (Exception e) {
            log.error("[KeepAlive] Application level DB ping failed", e);
        }
    }
}
