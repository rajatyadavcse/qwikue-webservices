package com.kitchen.order.dao;

import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.enums.PaymentStatus;
import com.kitchen.order.dto.response.OrderAppliedCharge;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kitchen.order.enums.OrderedBy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", schema = "order")
@Data
public class OrderDAO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    /**
     * References public.order_entity composite PK (entityNo + restaurantId).
     * Validated via restaurant-service before persisting.
     */
    @Column(name = "entity_no", nullable = false)
    private String entityNo;

    /**
     * References public.restaurant.restaurantId.
     * Validated via restaurant-service before persisting.
     */
    @Column(nullable = false)
    private Long restaurantId;

    @Column(name = "order_entity_type", length = 50)
    private String orderEntityType;

    @Column(name = "token_no")
    private Integer tokenNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "sub_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal subTotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "service_charge_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceChargeAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "taxes_and_charges", columnDefinition = "jsonb")
    private List<OrderAppliedCharge> taxesAndCharges = new ArrayList<>();

    /** Optional customer notes (e.g. "no onions") */
    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "prep_minutes")
    private Integer prepMinutes;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "initial_ready_at")
    private LocalDateTime initialReadyAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "actual_ready_at")
    private LocalDateTime actualReadyAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 20)
    private PaymentMode paymentMode = PaymentMode.CASH;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "ordered_by", nullable = false, length = 20, columnDefinition = "varchar(20) default 'CUSTOMER'")
    private OrderedBy orderedBy = OrderedBy.CUSTOMER;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerDAO customer;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemDAO> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = OrderStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
