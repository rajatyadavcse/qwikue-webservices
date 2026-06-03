package com.kitchen.order.service;

import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.dao.OrderItemDAO;
import com.kitchen.order.dto.request.CreateOrderRequest;
import com.kitchen.order.dto.request.OrderItemRequest;
import com.kitchen.order.dto.request.UpdateOrderStatusRequest;
import com.kitchen.order.dto.response.OrderItemResponse;
import com.kitchen.order.dto.response.OrderResponse;
import com.kitchen.order.dto.response.PagedResponse;
import com.kitchen.order.dto.response.OrderAppliedCharge;
import com.kitchen.order.dto.response.RestaurantChargeDto;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.exception.InvalidStatusTransitionException;
import com.kitchen.order.exception.ResourceNotFoundException;
import com.kitchen.order.mapper.OrderItemMapper;
import com.kitchen.order.mapper.OrderMapper;
import com.kitchen.order.repository.OrderItemRepository;
import com.kitchen.order.repository.OrderRepository;
import com.kitchen.order.dto.event.OrderUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // ── Create ─────────────────────────────────────────────────────────────────

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for restaurantId={}, tableNo={}", request.getRestaurantId(), request.getTableNo());

        // 1. Validate restaurant exists and get configurations
        RestaurantValidationService.RestaurantResponse restaurant = validationService.validateRestaurant(request.getRestaurantId());

        // 2. Validate table belongs to restaurant
        validationService.validateTable(request.getTableNo(), request.getRestaurantId());

        // 3. Build the order entity
        OrderDAO order = new OrderDAO();
        order.setRestaurantId(request.getRestaurantId());
        order.setTableNo(request.getTableNo());
        order.setNotes(request.getNotes());
        order.setStatus(OrderStatus.PENDING);

        // 4. Build order items — validate each menu item and snapshot price
        BigDecimal subTotal = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            // Validate menu item and get current price from restaurant-service
            RestaurantValidationService.MenuResponse menu = validationService
                    .validateMenuAndGetPrice(itemRequest.getMenuId());

            OrderItemDAO item = new OrderItemDAO();
            item.setMenuId(itemRequest.getMenuId());
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(menu.getPrice());
            item.setItemName(menu.getItemName());

            // Compute total_item_price = quantity × unit_price
            BigDecimal itemTotal = menu.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            item.setTotalItemPrice(itemTotal);

            // Link item to order
            item.setOrder(order);
            order.getItems().add(item);

            subTotal = subTotal.add(itemTotal);
        }

        order.setSubTotal(subTotal);

        // Calculate dynamic taxes and service charges
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal serviceChargeAmount = BigDecimal.ZERO;
        List<OrderAppliedCharge> appliedCharges = new ArrayList<>();

        if (restaurant.getTaxesAndCharges() != null) {
            for (RestaurantChargeDto charge : restaurant.getTaxesAndCharges()) {
                BigDecimal amount = BigDecimal.ZERO;
                if ("PERCENTAGE".equalsIgnoreCase(charge.getType())) {
                    amount = subTotal.multiply(charge.getValue()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                } else if ("FIXED".equalsIgnoreCase(charge.getType())) {
                    amount = charge.getValue();
                }

                if ("TAX".equalsIgnoreCase(charge.getCategory())) {
                    taxAmount = taxAmount.add(amount);
                } else if ("SERVICE_CHARGE".equalsIgnoreCase(charge.getCategory())) {
                    serviceChargeAmount = serviceChargeAmount.add(amount);
                }

                OrderAppliedCharge applied = new OrderAppliedCharge();
                applied.setName(charge.getName());
                applied.setType(charge.getType());
                applied.setAppliedRate(charge.getValue());
                applied.setCalculatedAmount(amount);
                appliedCharges.add(applied);
            }
        }

        order.setTaxAmount(taxAmount);
        order.setServiceChargeAmount(serviceChargeAmount);
        order.setTaxesAndCharges(appliedCharges);
        order.setTotalAmount(subTotal.add(taxAmount).add(serviceChargeAmount));

        // 5. Persist (cascade saves items too)
        OrderDAO saved = orderRepository.save(order);
        log.info("Order created successfully with orderId={}", saved.getOrderId());

        OrderResponse response = orderMapper.orderDAOToOrderResponse(saved);
        eventPublisher.publishEvent(new OrderUpdateEvent(this, response));
        return response;
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
            if (newStatus == OrderStatus.PREPARING && request.getPrepMinutes() != null) {
                // This is a delay request
                log.info("Delaying order {} by {} minutes. Reason: {}", orderId, request.getPrepMinutes(), request.getReason());
                
                int delayMin = request.getPrepMinutes();
                order.setPrepMinutes((order.getPrepMinutes() != null ? order.getPrepMinutes() : 0) + delayMin);
                
                LocalDateTime baseTime = order.getReadyAt() != null ? order.getReadyAt() : LocalDateTime.now();
                order.setReadyAt(baseTime.plusMinutes(delayMin));
                
                if (request.getReason() != null) {
                    order.setReason(request.getReason());
                }
                
                OrderDAO saved = orderRepository.save(order);
                log.info("Order {} delayed successfully. New readyAt: {}", orderId, order.getReadyAt());
                OrderResponse response = orderMapper.orderDAOToOrderResponse(saved);
                eventPublisher.publishEvent(new OrderUpdateEvent(this, response));
                return response;
            } else {
                throw new IllegalArgumentException("Current status and new status are the same");
            }
        }

        // Apply state transition rules
        if (newStatus == OrderStatus.PREPARING) {
            order.setAcceptedAt(LocalDateTime.now());
            if (request.getPrepMinutes() != null) {
                int prep = request.getPrepMinutes();
                order.setPrepMinutes(prep);
                order.setInitialReadyAt(order.getAcceptedAt().plusMinutes(prep));
                order.setReadyAt(order.getAcceptedAt().plusMinutes(prep));
            }
        } else if (newStatus == OrderStatus.READY) {
            order.setActualReadyAt(LocalDateTime.now());
        } else if (newStatus == OrderStatus.COMPLETED) {
            order.setCompletedAt(LocalDateTime.now());
        }

        order.setStatus(newStatus);
        if (request.getReason() != null) {
            order.setReason(request.getReason());
        }
        
        OrderDAO saved = orderRepository.save(order);

        log.info("Order {} status updated to {}", orderId, newStatus);
        OrderResponse response = orderMapper.orderDAOToOrderResponse(saved);
        eventPublisher.publishEvent(new OrderUpdateEvent(this, response));
        return response;
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
        OrderResponse response = orderMapper.orderDAOToOrderResponse(saved);
        eventPublisher.publishEvent(new OrderUpdateEvent(this, response));
        return response;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private OrderDAO findOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
    }
}
