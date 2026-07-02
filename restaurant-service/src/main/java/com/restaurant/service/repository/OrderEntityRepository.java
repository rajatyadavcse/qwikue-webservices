package com.restaurant.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.restaurant.service.dao.OrderEntityDAO;
import com.restaurant.service.dao.OrderEntityId;

@Repository
public interface OrderEntityRepository extends JpaRepository<OrderEntityDAO, OrderEntityId> {

    boolean existsByOrderEntityIdRestaurantId(Long restaurantId);

    List<OrderEntityDAO> findAllByOrderEntityIdRestaurantId(Long restaurantId);

}
