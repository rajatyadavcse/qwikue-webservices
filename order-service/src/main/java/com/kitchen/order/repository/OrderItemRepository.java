package com.kitchen.order.repository;

import com.kitchen.order.dao.OrderItemDAO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kitchen.order.repository.projection.TopItemProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemDAO, Long> {

    /** Fetch all items for a given order. */
    List<OrderItemDAO> findByOrderOrderId(Long orderId);

    /** Aggregate top ordered items for a restaurant within a date range and status list. */
    @Query(value = "SELECT oi.menu_id AS menuId, " +
                   "oi.item_name AS itemName, " +
                   "COALESCE(SUM(oi.quantity), 0) AS totalQuantitySold, " +
                   "COALESCE(SUM(oi.total_item_price), 0) AS totalRevenue, " +
                   "COUNT(DISTINCT o.order_id) AS orderCount " +
                   "FROM \"order\".order_items oi " +
                   "JOIN \"order\".orders o ON oi.order_id = o.order_id " +
                   "WHERE o.restaurant_id = :restaurantId " +
                   "AND o.status IN (:statuses) " +
                   "AND o.created_at >= :start AND o.created_at < :end " +
                   "GROUP BY oi.menu_id, oi.item_name " +
                   "ORDER BY totalQuantitySold DESC " +
                   "LIMIT :limit", nativeQuery = true)
    List<TopItemProjection> getTopOrderedItems(
            @Param("restaurantId") Long restaurantId,
            @Param("statuses") List<String> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("limit") int limit);
}

