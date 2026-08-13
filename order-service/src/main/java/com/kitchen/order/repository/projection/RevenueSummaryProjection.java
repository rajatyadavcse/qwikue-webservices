package com.kitchen.order.repository.projection;

import java.math.BigDecimal;

public interface RevenueSummaryProjection {
    BigDecimal getTotalRevenue();
    BigDecimal getNetSubTotal();
    BigDecimal getTotalTax();
    BigDecimal getTotalServiceCharge();
    BigDecimal getTotalDiscount();
    Long getTotalOrders();
    Double getAveragePrepTime();
}
