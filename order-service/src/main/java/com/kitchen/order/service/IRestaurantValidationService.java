package com.kitchen.order.service;

import com.restaurant.service.model.OrderEntityStatus;

public interface IRestaurantValidationService {

    RestaurantValidationService.RestaurantResponse validateRestaurant(Long restaurantId);

    RestaurantValidationService.EntityResponse validateEntity(String entityNo, Long restaurantId);

    RestaurantValidationService.MenuResponse validateMenuAndGetPrice(Long menuId);

    void updateEntityStatus(String entityNo, Long restaurantId, OrderEntityStatus status);
}
