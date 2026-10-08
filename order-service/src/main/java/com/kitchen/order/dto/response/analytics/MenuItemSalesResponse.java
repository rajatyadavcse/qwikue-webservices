package com.kitchen.order.dto.response.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemSalesResponse {
    private Long menuId;
    private Long restaurantId;
    private String itemName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Long totalQuantitySold;
    private BigDecimal totalRevenue;
    private Long orderCount;
    private BigDecimal averageSellingPrice;

    @Builder.Default
    private List<DailyItemSalesDTO> dailyTrends = new ArrayList<>();
}
