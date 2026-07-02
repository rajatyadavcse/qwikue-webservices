package com.restaurant.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.restaurant.service.dao.MenuDAO;

import java.util.List;

@Repository
public interface MenuRepository extends JpaRepository<MenuDAO, Long> {
    List<MenuDAO> findByRestaurantId(Long restaurantId);
}
