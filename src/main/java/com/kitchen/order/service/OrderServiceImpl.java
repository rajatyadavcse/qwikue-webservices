package com.kitchen.order.service;

import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.dao.OrderItemDAO;
import com.kitchen.order.dto.request.CreateOrderRequest;
import com.kitchen.order.dto.request.OrderItemRequest;
import com.kitchen.order.dto.request.UpdateOrderStatusRequest;
import com.kitchen.order.dto.response.OrderItemResponse;
import com.kitchen.order.dto.response.OrderResponse;
import com.kitchen.order.dto.response.PagedResponse;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.exception.InvalidStatusTransitionException;
import com.kitchen.order.exception.ResourceNotFoundException;
import com.kitchen.order.mapper.OrderItemMapper;
import com.kitchen.order.mapper.OrderMapper;
import com.kitchen.order.repository.OrderItemRepository;
import com.kitchen.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class OrderServiceImpl implements IOrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    /** Active statuses shown on the kitchen dashboard. */
    private static final List<OrderStatus> KITCHEN_ACTIVE_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.PREPARING,
            OrderStatus.READY);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private RestaurantValidationService validationService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    // ── Create ─────────────────────────────────────────────────────────────────

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for restaurantId={}, tableNo={}", request.getRestaurantId(), request.getTableNo());

        // 1. Validate restaurant exists
        validationService.validateRestaurant(request.getRestaurantId());

        // 2. Validate table belongs to restaurant
        validationService.validateTable(request.getTableNo(), request.getRestaurantId());

        // 3. Build the order entity
        OrderDAO order = new OrderDAO();
        order.setRestaurantId(request.getRestaurantId());
        order.setTableNo(request.getTableNo());
        order.setNotes(request.getNotes());
        order.setStatus(OrderStatus.PENDING);

        // 4. Build order items — validate each menu item and snapshot price
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            // Validate menu item and get current price from restaurant-service
            RestaurantValidationService.MenuResponse menu = validationService
                    .validateMenuAndGetPrice(itemRequest.getMenuId());

            OrderItemDAO item = new OrderItemDAO();
            item.setMenuId(itemRequest.getMenuId());
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(menu.getPrice());

            // Compute total_item_price = quantity × unit_price
            BigDecimal itemTotal = menu.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            item.setTotalItemPrice(itemTotal);

            // Link item to order
            item.setOrder(order);
            order.getItems().add(item);

            totalAmount = totalAmount.add(itemTotal);
        }

        order.setTotalAmount(totalAmount);

        // 5. Persist (cascade saves items too)
        OrderDAO saved = orderRepository.save(order);
        log.info("Order created successfully with orderId={}", saved.getOrderId());

        return orderMapper.orderDAOToOrderResponse(saved);
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        OrderDAO order = findOrderById(orderId);
        return orderMapper.orderDAOToOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrdersByRestaurant(Long restaurantId, OrderStatus status,
            Pageable pageable) {
        Page<OrderDAO> page;

        if (status != null) {
            page = orderRepository.findByRestaurantIdAndStatus(restaurantId, status, pageable);
        } else {
            page = orderRepository.findByRestaurantId(restaurantId, pageable);
        }

        List<OrderResponse> content = orderMapper.orderDAOListToResponseList(page.getContent());

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderItemResponse> getOrderItems(Long orderId) {
        // Ensure order exists before fetching items
        findOrderById(orderId);
        List<OrderItemDAO> items = orderItemRepository.findByOrderOrderId(orderId);
        return orderItemMapper.orderItemDAOListToResponseList(items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getKitchenOrders(Long restaurantId) {
        List<OrderDAO> activeOrders = orderRepository.findByRestaurantIdAndStatusIn(restaurantId,
                KITCHEN_ACTIVE_STATUSES);
        return orderMapper.orderDAOListToResponseList(activeOrders);
    }

    // ── Update Status ──────────────────────────────────────────────────────────

    @Override
    public OrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request) {
        OrderDAO order = findOrderById(orderId);
        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();

        log.info("Updating order {} status: {} → {}", orderId, currentStatus, newStatus);

        if (newStatus == null) {
            throw new IllegalArgumentException("Order status cannot be null or empty");
        }

        if (currentStatus == newStatus) {
            throw new IllegalArgumentException("Current status and new status are the same");
        }

        order.setStatus(newStatus);
        order.setReason(request.getReason());
        OrderDAO saved = orderRepository.save(order);

        log.info("Order {} status updated to {}", orderId, newStatus);
        return orderMapper.orderDAOToOrderResponse(saved);
    }

    // ── Cancel (soft delete) ───────────────────────────────────────────────────

    @Override
    public OrderResponse cancelOrder(Long orderId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required when cancelling an order");
        }

        OrderDAO order = findOrderById(orderId);
        OrderStatus currentStatus = order.getStatus();

        if (!currentStatus.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new InvalidStatusTransitionException(currentStatus, OrderStatus.CANCELLED);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setReason(reason);
        OrderDAO saved = orderRepository.save(order);

        log.info("Order {} cancelled. Reason: {}", orderId, reason);
        return orderMapper.orderDAOToOrderResponse(saved);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private OrderDAO findOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }
}
