package com.kitchen.order.dto.response.dashboard;

import com.kitchen.order.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTypeBreakdownDTO {
    private OrderType orderType;
    private BigDecimal amount;
    private Long orderCount;
    private BigDecimal percentage;
}
