package com.restaurant.service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import com.restaurant.service.dao.MenuDAO;
import com.restaurant.service.exception.ResourceNotFoundException;
import com.restaurant.service.mapper.MenuMapper;
import com.restaurant.service.model.Menu;
import com.restaurant.service.repository.MenuRepository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MenuServiceImpl implements IMenuService {

    @Autowired
    MenuRepository menuRepository;

    @Autowired
    MenuMapper mapper;

    @Override
    @Caching(evict = {
            @CacheEvict(value = "menuByRestaurant", key = "#menu.restaurantId", condition = "#menu.restaurantId != null"),
            @CacheEvict(value = "menuBatch", allEntries = true)
    })
    public Menu createMenu(Menu menu) {
        menu.setMenuId(null); // ensure Hibernate treats this as a new entity
        if (menu.getCreatedDate() == null) {
            menu.setCreatedDate(new Date());
        }
        menu.setUpdatedDate(new Date());
        return mapper.menuDAOToMenu(menuRepository.save(mapper.menuToMenuDAO(menu)));
    }

    @Override
    @Cacheable(value = "menuByRestaurant", key = "#restaurantId")
    public List<Menu> getMenuByRestaurantId(Long restaurantId) {
        return menuRepository.findByRestaurantId(restaurantId).stream()
                .map(mapper::menuDAOToMenu)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "menuItems", key = "#id")
    public Menu getMenuById(Long id) {
        MenuDAO menuDAO = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu not found with id: " + id));
        return mapper.menuDAOToMenu(menuDAO);
    }

    @Override
    @Cacheable(value = "menuBatch", key = "#ids")
    public List<Menu> getMenusByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return menuRepository.findByMenuIdIn(ids).stream()
                .map(mapper::menuDAOToMenu)
                .collect(Collectors.toList());
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "menuItems", key = "#id"),
            @CacheEvict(value = "menuByRestaurant", allEntries = true),
            @CacheEvict(value = "menuBatch", allEntries = true)
    })
    public Menu updateMenu(Long id, Menu menuDetails) {
        MenuDAO existingMenuDAO = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu not found with id: " + id));

        existingMenuDAO.setItemName(menuDetails.getItemName());
        existingMenuDAO.setDescription(menuDetails.getDescription());
        existingMenuDAO.setPrice(menuDetails.getPrice());
        existingMenuDAO.setCategory(menuDetails.getCategory());
        existingMenuDAO.setIsAvailable(menuDetails.getIsAvailable());
        existingMenuDAO.setUpdatedDate(new Date());
        existingMenuDAO.setIsVeg(menuDetails.getIsVeg());
        existingMenuDAO.setImageUrl(menuDetails.getImageUrl());
        return mapper.menuDAOToMenu(menuRepository.save(existingMenuDAO));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "menuItems", key = "#id"),
            @CacheEvict(value = "menuByRestaurant", allEntries = true),
            @CacheEvict(value = "menuBatch", allEntries = true)
    })
    public void deleteMenu(Long id) {
        MenuDAO existingMenuDAO = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu not found with id: " + id));

        menuRepository.delete(existingMenuDAO);
    }
}
