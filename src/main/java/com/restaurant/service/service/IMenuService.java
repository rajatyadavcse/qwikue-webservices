package com.restaurant.service.service;

import java.util.List;

import com.restaurant.service.model.Menu;

public interface IMenuService {

    Menu createMenu(Menu menu);

    List<Menu> getMenuByRestaurantId(Long restaurantId);

    Menu updateMenu(Long id, Menu menuDetails);

    void deleteMenu(Long id);
}
