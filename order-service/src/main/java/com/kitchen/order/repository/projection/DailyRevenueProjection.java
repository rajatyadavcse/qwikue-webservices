package com.kitchen.order.repository.projection;

import java.math.BigDecimal;

public interface DailyRevenueProjection {
    String getDateStr();
    BigDecimal getRevenue();
    Long getOrderCount();
}
