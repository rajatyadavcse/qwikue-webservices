package com.kitchen.order.repository;

import com.kitchen.order.dao.OrderItemDAO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemDAO, Long> {

    /** Fetch all items for a given order. */
    List<OrderItemDAO> findByOrderOrderId(Long orderId);
}
