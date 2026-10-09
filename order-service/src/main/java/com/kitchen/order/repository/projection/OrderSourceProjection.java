package com.kitchen.order.repository.projection;

import java.math.BigDecimal;

public interface OrderSourceProjection {
    String getOrderedBy();
    Long getOrderCount();
    BigDecimal getRevenue();
}
