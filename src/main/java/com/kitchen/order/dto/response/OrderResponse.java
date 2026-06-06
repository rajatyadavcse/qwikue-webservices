package com.kitchen.order.dto.response;

import com.kitchen.order.enums.OrderStatus;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponse {

    private Long orderId;
    private Long restaurantId;
    private String entityNo;
    private String orderEntityType;
    private OrderStatus status;
    private BigDecimal subTotal;
    private BigDecimal taxAmount;
    private BigDecimal serviceChargeAmount;
    private BigDecimal totalAmount;
    private List<OrderAppliedCharge> taxesAndCharges;
    private String notes;
    private String reason;
    private Integer prepMinutes;
    private LocalDateTime acceptedAt;
    private LocalDateTime initialReadyAt;
    private LocalDateTime readyAt;
    private LocalDateTime actualReadyAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemResponse> items;
}
