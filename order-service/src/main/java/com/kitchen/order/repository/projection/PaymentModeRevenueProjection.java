package com.kitchen.order.repository.projection;

import com.kitchen.order.enums.PaymentMode;
import java.math.BigDecimal;

public interface PaymentModeRevenueProjection {
    PaymentMode getPaymentMode();
    BigDecimal getAmount();
    Long getOrderCount();
}
