package com.kitchen.order.repository.projection;

import com.kitchen.order.enums.OrderStatus;

public interface OrderStatusCountProjection {
    OrderStatus getStatus();
    Long getOrderCount();
}
