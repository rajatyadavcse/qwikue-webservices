package com.restaurant.service.mapper;

import org.mapstruct.Mapper;

import com.restaurant.service.dao.RestaurantDAO;
import com.restaurant.service.model.Restaurant;

@Mapper(componentModel = "spring")
public interface RestaurantMapper {

    RestaurantDAO restaurantToRestaurantDAO(Restaurant restaurant);

    Restaurant restaurantDAOToRestaurant(RestaurantDAO restaurantDAO);

}
