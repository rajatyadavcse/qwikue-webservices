package com.restaurant.service.event;

import com.restaurant.service.model.OrderEntity;
import org.springframework.context.ApplicationEvent;

public class OrderEntityUpdateEvent extends ApplicationEvent {

    private final OrderEntity orderEntity;

    public OrderEntityUpdateEvent(Object source, OrderEntity orderEntity) {
        super(source);
        this.orderEntity = orderEntity;
    }

    public OrderEntity getOrderEntity() {
        return orderEntity;
    }
}
