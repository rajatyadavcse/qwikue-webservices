package com.restaurant.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates Spring's scheduling infrastructure only when running under the
 * "production" profile.  This prevents the scheduler thread-pool from being
 * spun up during local development or test runs.
 */
@Configuration
@Profile("production")
@EnableScheduling
public class SchedulingConfig {
}
