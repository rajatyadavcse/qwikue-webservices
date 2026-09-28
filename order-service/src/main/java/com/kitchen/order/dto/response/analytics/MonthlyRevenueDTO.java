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
public class MonthlyRevenueDTO {
    private Integer year;
    private Integer month;
    private String yearMonth;
    private BigDecimal revenue;
    private Long orderCount;
    private BigDecimal growthRatePercentage;
}
