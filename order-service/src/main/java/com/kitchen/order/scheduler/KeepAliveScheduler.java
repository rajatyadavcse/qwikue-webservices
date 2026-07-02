package com.kitchen.order.scheduler;

import com.kitchen.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires a lightweight DB query on a schedule to prevent Render's free-tier
 * from spinning down the service after 15 minutes of inactivity.
 *
 * Activated only when {@code app.keep-alive.cron} is set in the active profile.
 */
@Component
@ConditionalOnProperty(name = "app.keep-alive.cron")
public class KeepAliveScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveScheduler.class);

    @Autowired
    private OrderRepository orderRepository;

    @Scheduled(cron = "${app.keep-alive.cron}")
    public void keepAlive() {
        try {
            long count = orderRepository.count();
            log.debug("Keep-alive ping: orders table reachable, count={}", count);
        } catch (Exception e) {
            log.warn("Keep-alive ping failed: {}", e.getMessage());
        }
    }
}
