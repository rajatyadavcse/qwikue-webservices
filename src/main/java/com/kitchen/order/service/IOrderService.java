package com.kitchen.order.service;

import com.kitchen.order.dto.request.CreateOrderRequest;
import com.kitchen.order.dto.request.UpdateOrderStatusRequest;
import com.kitchen.order.dto.response.OrderItemResponse;
import com.kitchen.order.dto.response.OrderResponse;
import com.kitchen.order.dto.response.PagedResponse;
import com.kitchen.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

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
    PagedResponse<OrderResponse> getOrdersByRestaurant(Long restaurantId, OrderStatus status, Pageable pageable);

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
}
