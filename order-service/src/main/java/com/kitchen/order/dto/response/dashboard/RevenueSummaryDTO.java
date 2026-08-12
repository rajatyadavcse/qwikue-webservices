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
public class RevenueSummaryDTO {
    private BigDecimal totalRevenue;
    private BigDecimal netSubTotal;
    private BigDecimal totalTax;
    private BigDecimal totalServiceCharge;
    private BigDecimal totalDiscount;
    private Long totalOrders;
    private BigDecimal averageOrderValue;
}
