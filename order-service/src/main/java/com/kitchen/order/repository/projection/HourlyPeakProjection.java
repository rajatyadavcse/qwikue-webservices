package com.kitchen.order.repository.projection;

import java.math.BigDecimal;

public interface HourlyPeakProjection {
    Integer getHourOfDay();
    Long getOrderCount();
    BigDecimal getRevenue();
}
