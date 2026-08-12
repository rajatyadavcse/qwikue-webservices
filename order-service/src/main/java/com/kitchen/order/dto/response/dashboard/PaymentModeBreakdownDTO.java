package com.kitchen.order.dto.response.dashboard;

import com.kitchen.order.enums.PaymentMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentModeBreakdownDTO {
    private PaymentMode paymentMode;
    private BigDecimal amount;
    private Long orderCount;
    private BigDecimal percentage;
}
