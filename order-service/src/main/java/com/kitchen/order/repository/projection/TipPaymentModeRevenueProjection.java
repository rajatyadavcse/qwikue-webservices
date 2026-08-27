package com.kitchen.order.repository.projection;

import java.math.BigDecimal;

public interface TipPaymentModeRevenueProjection {
    String getTipPaymentMode();
    BigDecimal getAmount();
    Long getOrderCount();
}
