package com.kitchen.order.dto.event;

import com.kitchen.order.dto.response.OrderResponse;
import org.springframework.context.ApplicationEvent;

public class OrderUpdateEvent extends ApplicationEvent {
    private final OrderResponse order;

    public OrderUpdateEvent(Object source, OrderResponse order) {
        super(source);
        this.order = order;
    }

    public OrderResponse getOrder() {
        return order;
    }
}
