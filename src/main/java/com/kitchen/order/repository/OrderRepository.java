package com.kitchen.order.repository;

import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.enums.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<OrderDAO, Long> {

    /** Fetch all orders for a restaurant, paginated. */
    Page<OrderDAO> findByRestaurantId(Long restaurantId, Pageable pageable);

    /** Fetch all orders for a restaurant excluding a specific status, paginated. */
    Page<OrderDAO> findByRestaurantIdAndStatusNot(Long restaurantId, OrderStatus status, Pageable pageable);

    /** Fetch orders for a restaurant filtered by status, paginated. */
    Page<OrderDAO> findByRestaurantIdAndStatus(Long restaurantId, OrderStatus status, Pageable pageable);

    /** Fetch active kitchen orders (non-terminal statuses) for a specific restaurant. */
    List<OrderDAO> findByRestaurantIdAndStatusIn(Long restaurantId, List<OrderStatus> statuses);

    /** Fetch online orders that are pending payment and created before the threshold time. */
    List<OrderDAO> findByPaymentModeAndPaymentStatusAndCreatedAtBefore(
            PaymentMode paymentMode, PaymentStatus paymentStatus, LocalDateTime threshold);
}


