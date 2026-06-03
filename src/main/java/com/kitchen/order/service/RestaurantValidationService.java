package com.kitchen.order.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kitchen.order.dto.response.RestaurantChargeDto;
import com.kitchen.order.exception.ExternalServiceException;
import com.kitchen.order.exception.ResourceNotFoundException;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validates restaurant, table, and menu entities against the restaurant-service
 * using synchronous RestClient calls before an order is persisted.
 */
@Service
public class RestaurantValidationService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantValidationService.class);

    @Autowired
    @Qualifier("restaurantServiceClient")
    private RestClient restaurantServiceClient;

    // ── Inner response models (mirrors restaurant-service response shapes) ────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RestaurantResponse {
        private Long restaurantId;
        private String restaurantName;
        private String status;
        private List<RestaurantChargeDto> taxesAndCharges;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableResponse {
        private Long tableNo;
        private Long restaurantId;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MenuResponse {
        private Long menuId;
        private Long restaurantId;
        private String itemName;
        private BigDecimal price;
        private Boolean isAvailable;
    }

    // ── Validation methods ─────────────────────────────────────────────────────

    /**
     * Validates that a restaurant with the given ID exists.
     *
     * @return RestaurantResponse containing restaurant details and tax configuration
     * @throws ResourceNotFoundException if restaurant not found (404)
     * @throws ExternalServiceException  if restaurant-service is unreachable or errors
     */
    public RestaurantResponse validateRestaurant(Long restaurantId) {
        log.debug("Validating restaurantId={}", restaurantId);
        try {
            return restaurantServiceClient.get()
                    .uri("/restaurants/{id}", restaurantId)
                    .retrieve()
                    .body(RestaurantResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Restaurant not found with id: " + restaurantId);
        } catch (RestClientException e) {
            log.error("restaurant-service call failed for restaurantId={}: {}", restaurantId, e.getMessage());
            throw new ExternalServiceException("restaurant-service is currently unavailable. Please try again later.", e);
        }
    }

    /**
     * Validates that a dining table exists for the given restaurant.
     *
     * @throws ResourceNotFoundException if table not found (404)
     * @throws ExternalServiceException  if restaurant-service is unreachable or errors
     */
    public void validateTable(Long tableNo, Long restaurantId) {
        log.debug("Validating tableNo={}, restaurantId={}", tableNo, restaurantId);
        try {
            restaurantServiceClient.get()
                    .uri("/tables/{tableNo}/restaurant/{restaurantId}", tableNo, restaurantId)
                    .retrieve()
                    .body(TableResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException(
                    String.format("Table no %d not found for restaurantId: %d", tableNo, restaurantId));
        } catch (RestClientException e) {
            log.error("restaurant-service call failed for tableNo={}, restaurantId={}: {}", tableNo, restaurantId, e.getMessage());
            throw new ExternalServiceException("restaurant-service is currently unavailable. Please try again later.", e);
        }
    }

    /**
     * Validates that a menu item exists and fetches its current price for snapshotting.
     *
     * @return MenuResponse containing the current unit price
     * @throws ResourceNotFoundException if menu item not found (404)
     * @throws ExternalServiceException  if restaurant-service is unreachable or errors
     */
    public MenuResponse validateMenuAndGetPrice(Long menuId) {
        log.debug("Validating menuId={}", menuId);
        try {
            MenuResponse menu = restaurantServiceClient.get()
                    .uri("/menu/{id}", menuId)
                    .retrieve()
                    .body(MenuResponse.class);

            if (menu == null) {
                throw new ResourceNotFoundException("Menu item not found with id: " + menuId);
            }
            if (Boolean.FALSE.equals(menu.getIsAvailable())) {
                throw new IllegalArgumentException(
                        "Menu item '" + menu.getItemName() + "' (id: " + menuId + ") is currently unavailable");
            }
            return menu;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Menu item not found with id: " + menuId);
        } catch (ResourceNotFoundException | IllegalArgumentException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("restaurant-service call failed for menuId={}: {}", menuId, e.getMessage());
            throw new ExternalServiceException("restaurant-service is currently unavailable. Please try again later.", e);
        }
    }
}
