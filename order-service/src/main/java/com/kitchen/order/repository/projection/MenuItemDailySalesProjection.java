package com.kitchen.order.repository.projection;

import java.math.BigDecimal;

public interface MenuItemDailySalesProjection {
    String getDateStr();
    Long getQuantitySold();
    BigDecimal getRevenue();
    Long getOrderCount();
}
