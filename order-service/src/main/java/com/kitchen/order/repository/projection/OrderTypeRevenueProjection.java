package com.kitchen.order.repository.projection;

import com.kitchen.order.enums.OrderType;
import java.math.BigDecimal;

public interface OrderTypeRevenueProjection {
    OrderType getOrderType();
    BigDecimal getAmount();
    Long getOrderCount();
}
