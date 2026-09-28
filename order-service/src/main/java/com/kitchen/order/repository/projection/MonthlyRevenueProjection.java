package com.kitchen.order.repository.projection;

import java.math.BigDecimal;

public interface MonthlyRevenueProjection {
    Integer getYearVal();
    Integer getMonthVal();
    BigDecimal getRevenue();
    Long getOrderCount();
}
