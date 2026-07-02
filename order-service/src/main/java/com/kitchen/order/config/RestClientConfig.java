package com.kitchen.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * RestClient pre-configured with the restaurant-service base URL.
     * Injected into RestaurantValidationService to validate restaurantId, tableNo, and menuId.
     *
     * Base URL is set via:
     *   - Local:      app.restaurant-service.base-url in application-local.yml
     *   - Production: override via APP_RESTAURANT_SERVICE_BASE_URL environment variable on the platform
     */
    @Bean
    public RestClient restaurantServiceClient(
            @Value("${app.restaurant-service.base-url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
