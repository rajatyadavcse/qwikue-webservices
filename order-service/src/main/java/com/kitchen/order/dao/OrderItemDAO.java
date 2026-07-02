package com.kitchen.order.dao;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items", schema = "order")
@Data
public class OrderItemDAO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderDAO order;

    /**
     * References public.menu.menuId.
     * Validated via restaurant-service before persisting.
     */
    @Column(nullable = false)
    private Long menuId;

    @Column(nullable = false)
    private Integer quantity;

    /**
     * Price snapshot at the time of order placement.
     * Intentionally not a FK — decoupled from future menu price changes.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** Computed: quantity × unitPrice */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalItemPrice;

    @Column(name = "item_name")
    private String itemName;
}
