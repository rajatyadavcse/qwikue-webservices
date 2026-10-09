package com.kitchen.order.repository.projection;

import java.math.BigDecimal;

public interface TopItemProjection {
    Long getMenuId();
    String getItemName();
    Long getTotalQuantitySold();
    BigDecimal getTotalRevenue();
    Long getOrderCount();
}
