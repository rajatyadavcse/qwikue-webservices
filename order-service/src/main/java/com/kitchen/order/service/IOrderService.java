package com.kitchen.order.service;

import com.kitchen.order.dto.request.CreateOrderRequest;
import com.kitchen.order.dto.request.OrderDiscountRequest;
import com.kitchen.order.dto.request.UpdateOrderStatusRequest;
import com.kitchen.order.dto.response.OrderItemResponse;
import com.kitchen.order.dto.response.OrderResponse;
import com.kitchen.order.dto.response.PagedResponse;
import com.kitchen.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface IOrderService {

    /**
     * Creates a new order after validating restaurant, table, and all menu items.
     * Prices are fetched from restaurant-service and snapshotted.
     */
    OrderResponse createOrder(CreateOrderRequest request);

    /**
     * Retrieves a single order by its ID including all items.
     */
    OrderResponse getOrderById(Long orderId);

    /**
     * Lists all orders for a restaurant with optional status filter, paginated.
     */
    PagedResponse<OrderResponse> getOrdersByRestaurant(Long restaurantId, OrderStatus status, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    /**
     * Updates the status of an order to any new status.
     * Reason is optional for all status updates.
     */
    OrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request);

    /**
     * Cancels an order (soft delete) — sets status to CANCELLED with a mandatory reason.
     */
    OrderResponse cancelOrder(Long orderId, String reason);

    /**
     * Returns all items belonging to a specific order.
     */
    List<OrderItemResponse> getOrderItems(Long orderId);

    /**
     * Returns all active (non-terminal) orders for the kitchen dashboard of a restaurant.
     * Active = PENDING, PREPARING, READY
     */
    List<OrderResponse> getKitchenOrders(Long restaurantId);

    /**
     * Completes payment for an order, updates payment status, and sets the transaction reference.
     */
    OrderResponse completePayment(Long orderId, String razorpayPaymentId);

    /**
     * Marks payment as failed for an order, updates payment status, and sets the failure reason.
     */
    OrderResponse failPayment(Long orderId, String errorMessage);

    /**
     * Retrieves the currently active order (status PENDING to READY) for a given restaurant and entity (table).
     * Returns null if no active order is found.
     */
    OrderResponse getCurrentOrderByEntity(Long restaurantId, String entityNo);

    /**
     * Retrieves currently active orders (status PENDING to READY) for a given restaurant,
     * optionally filtered by entity (table).
     * If entityNo is provided, returns active order for that entity in a list.
     * If entityNo is null/blank, returns all active orders for the restaurant.
     */
    List<OrderResponse> getCurrentOrders(Long restaurantId, String entityNo);

    /**
     * Applies or updates an order-level discount on an existing order and recalculates totals.
     */
    OrderResponse applyOrderDiscount(Long orderId, OrderDiscountRequest request);

    /**
     * Removes an order-level discount from an existing order and recalculates totals.
     */
    OrderResponse removeOrderDiscount(Long orderId);
}


