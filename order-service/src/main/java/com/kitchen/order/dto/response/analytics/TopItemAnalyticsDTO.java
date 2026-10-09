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
public class TopItemAnalyticsDTO {
    private Long menuId;
    private String itemName;
    private Long totalQuantitySold;
    private BigDecimal totalRevenue;
    private Long orderCount;
}
