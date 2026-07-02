package com.kitchen.order.exception;

import com.kitchen.order.enums.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super(String.format("Cannot transition order status from '%s' to '%s'", from, to));
    }
}
