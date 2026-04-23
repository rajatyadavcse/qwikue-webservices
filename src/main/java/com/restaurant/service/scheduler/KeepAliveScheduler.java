package com.restaurant.service.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.restaurant.service.repository.RestaurantRepository;

@Component
@Profile("production")
public class KeepAliveScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveScheduler.class);

    private final RestaurantRepository restaurantRepository;

    public KeepAliveScheduler(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    /**
     * Runs every 10 minutes in production.
     *
     * Cron expression: 0 0/10 * * * *
     * ┌─ second (0)
     * │ ┌─ minute (every 10 min)
     * │ │ ┌─ hour (every)
     * │ │ │ ┌─ day-of-month (every)
     * │ │ │ │ ┌─ month (every)
     * │ │ │ │ │ └─ day-of-week (every)
     * 0 0/10 * * * *
     *
     * The interval is intentionally shorter than Render's 15-minute sleep
     * threshold to guarantee the service always receives at least one
     * "activity" event before the timeout is reached.
     */
    @Scheduled(cron = "${app.keep-alive.cron:0 0/10 * * * *}")
    public void keepDatabaseConnectionAlive() {
        try {
            // Just run a simple count query — very lightweight
            long count = restaurantRepository.count();
            log.info("Keep-alive: Queried database, found {} restaurants", count);
        } catch (Exception e) {
            log.error("Keep-alive failed", e);
        }
    }
}
