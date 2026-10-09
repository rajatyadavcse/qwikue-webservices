package com.kitchen.order.dto.response.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedAnalyticsResponse {
    private Long restaurantId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer monthsLimit;

    @Builder.Default
    private List<DailyRevenueDTO> dailyTrends = new ArrayList<>();

    @Builder.Default
    private List<MonthlyRevenueDTO> monthlyTrends = new ArrayList<>();

    @Builder.Default
    private List<TopItemAnalyticsDTO> topItemsToday = new ArrayList<>();

    @Builder.Default
    private List<TopItemAnalyticsDTO> topItemsForRange = new ArrayList<>();

    @Builder.Default
    private List<HourlyPeakDTO> hourlyPeaks = new ArrayList<>();

    @Builder.Default
    private List<OrderSourceBreakdownDTO> orderSourceBreakdown = new ArrayList<>();
}
