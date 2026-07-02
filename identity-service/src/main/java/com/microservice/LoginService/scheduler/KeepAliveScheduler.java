package com.microservice.LoginService.scheduler;

import com.microservice.LoginService.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * KeepAliveScheduler — prevents the Render free-tier service from sleeping.
 *
 * Render's free tier spins down a web service after 15 minutes of inactivity.
 * This scheduler fires a lightweight DB query every 10 minutes so the process
 * is never considered "idle" by Render's idle-detection mechanism.
 *
 * The scheduler is restricted to the "prod" profile so it never runs during
 * local development.
 */
@Component
@Profile("prod")
public class KeepAliveScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveScheduler.class);

    private final UserRepository userRepository;

    public KeepAliveScheduler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Runs every 10 minutes in production.
     *
     * Cron expression:  0 0/10 * * * *
     *   ┌─ second  (0)
     *   │  ┌─ minute (every 10 min)
     *   │  │     ┌─ hour   (every)
     *   │  │     │  ┌─ day-of-month (every)
     *   │  │     │  │  ┌─ month (every)
     *   │  │     │  │  │  └─ day-of-week (every)
     *   0  0/10  *  *  *  *
     *
     * The interval is intentionally shorter than Render's 15-minute sleep
     * threshold to guarantee the service always receives at least one
     * "activity" event before the timeout is reached.
     */
    @Scheduled(cron = "${app.keep-alive.cron:0 0/10 * * * *}")
    public void keepAlive() {
        try {
            long count = userRepository.count();
            log.info("[KeepAlive] DB ping successful — total users in DB: {}", count);
        } catch (Exception ex) {
            log.warn("[KeepAlive] DB ping failed: {}", ex.getMessage());
        }
    }
}
