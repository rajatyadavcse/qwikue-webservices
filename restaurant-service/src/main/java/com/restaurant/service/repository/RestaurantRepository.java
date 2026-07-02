package com.restaurant.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.restaurant.service.dao.RestaurantDAO;

@Repository
public interface RestaurantRepository extends JpaRepository<RestaurantDAO, Long> {
}
