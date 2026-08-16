package com.kitchen.order.service;

import com.restaurant.service.model.OrderEntityStatus;

import java.util.List;
import java.util.Map;

public interface IRestaurantValidationService {

    RestaurantValidationService.RestaurantResponse validateRestaurant(Long restaurantId);

    RestaurantValidationService.EntityResponse validateEntity(String entityNo, Long restaurantId);

    RestaurantValidationService.MenuResponse validateMenuAndGetPrice(Long menuId);

    default Map<Long, RestaurantValidationService.MenuResponse> validateMenusAndGetPrices(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<Long, RestaurantValidationService.MenuResponse> map = new java.util.HashMap<>();
        for (Long menuId : menuIds) {
            map.put(menuId, validateMenuAndGetPrice(menuId));
        }
        return map;
    }

    void updateEntityStatus(String entityNo, Long restaurantId, com.restaurant.service.model.OrderEntityStatus status);
}
