package com.kitchen.order.service;

import com.kitchen.order.dto.response.analytics.AdvancedAnalyticsResponse;
import com.kitchen.order.enums.OrderStatus;

import java.time.LocalDate;
import java.util.List;

public interface IAdvancedAnalyticsService {

    /**
     * Aggregates daily trends, monthly trends (with MoM growth rate), top items today/range,
     * 24-hour peak hour heatmaps, and channel breakdowns concurrently using CompletableFuture.
     *
     * @param restaurantId Restaurant ID
     * @param fromDate     Start date for daily trends (inclusive, Asia/Kolkata)
     * @param toDate       End date for daily trends (inclusive, Asia/Kolkata)
     * @param monthsLimit  Number of months to include in Month-on-Month trends (1 to 36)
     * @param topLimit     Limit for top items list (1 to 50)
     * @param statuses     Optional list of order statuses (defaults to COMPLETED)
     * @return AdvancedAnalyticsResponse
     */
    AdvancedAnalyticsResponse getAdvancedAnalytics(
            Long restaurantId,
            LocalDate fromDate,
            LocalDate toDate,
            Integer monthsLimit,
            Integer topLimit,
            List<OrderStatus> statuses);
}
