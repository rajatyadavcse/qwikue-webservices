package com.kitchen.order.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubPaymentModeBreakdownDTO {
    private String subPaymentMode;
    private BigDecimal amount;
    private Long orderCount;
    private BigDecimal percentage;
}
