package com.restaurant.service.service;

import java.util.List;

import com.restaurant.service.model.Restaurant;

public interface IRestaurantService {

    Restaurant createRestaurant(Restaurant restaurant);

    List<Restaurant> getAllRestaurants();

    Restaurant getRestaurantById(Long id);

    Restaurant updateRestaurant(Long id, Restaurant restaurantDetails);

    void deleteRestaurant(Long id);
}
