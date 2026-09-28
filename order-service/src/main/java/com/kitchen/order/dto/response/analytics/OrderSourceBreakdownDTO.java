package com.kitchen.order.dto.response.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSourceBreakdownDTO {
    private String orderedBy;
    private Long orderCount;
    private BigDecimal revenue;
    private BigDecimal percentage;
}
