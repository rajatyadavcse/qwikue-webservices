package com.restaurant.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.service.dao.TableDAO;
import com.restaurant.service.dao.TableId;

public interface TableRepository extends JpaRepository<TableDAO, TableId> {

    boolean existsByRestaurantId(Long restaurantId);

    List<TableDAO> findAllByRestaurantId(Long restaurantId);

}
