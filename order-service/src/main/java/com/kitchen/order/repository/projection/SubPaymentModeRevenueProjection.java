package com.kitchen.order.repository.projection;

import java.math.BigDecimal;

public interface SubPaymentModeRevenueProjection {
    String getSubPaymentMode();
    BigDecimal getAmount();
    Long getOrderCount();
}
