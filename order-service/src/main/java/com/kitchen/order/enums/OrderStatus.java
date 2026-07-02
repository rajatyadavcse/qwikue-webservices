package com.kitchen.order.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents the lifecycle status of an order.
 *
 * Valid transitions:
 *   PENDING    → PREPARING, CANCELLED
 *   PREPARING  → READY,     CANCELLED
 *   READY      → COMPLETED
 *   COMPLETED  → (terminal — no further transitions)
 *   CANCELLED  → (terminal — no further transitions)
 */
public enum OrderStatus {

    PAYMENT_PENDING,
    PENDING,
    PREPARING,
    READY,
    COMPLETED,
    CANCELLED,
    REJECTED;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            PAYMENT_PENDING, EnumSet.of(PENDING, CANCELLED),
            PENDING,   EnumSet.of(PREPARING, CANCELLED),
            PREPARING, EnumSet.of(READY, CANCELLED),
            READY,     EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            REJECTED,  EnumSet.noneOf(OrderStatus.class)
    );

    /**
     * Returns true if transitioning from {@code this} status to {@code next} is allowed.
     */
    public boolean canTransitionTo(OrderStatus next) {
        return VALID_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(OrderStatus.class)).contains(next);
    }
}
