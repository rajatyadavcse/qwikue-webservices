package com.kitchen.order.service;

import com.kitchen.order.dto.response.dashboard.DashboardRevenueResponse;
import com.kitchen.order.enums.OrderStatus;

import java.time.LocalDate;
import java.util.List;

public interface IDashboardAnalyticsService {

    /**
     * Aggregates and calculates dashboard revenue, payment mode breakdowns,
     * sub-payment mode breakdowns, and order type statistics for a given restaurant and date range.
     *
     * @param restaurantId Restaurant ID
     * @param fromDate     Start date (inclusive, Asia/Kolkata timezone)
     * @param toDate       End date (inclusive, Asia/Kolkata timezone)
     * @param statuses     Optional list of statuses to include in revenue; if null/empty, defaults to valid paid/completed statuses.
     * @return DashboardRevenueResponse containing summary and breakdowns.
     */
    DashboardRevenueResponse getDashboardRevenue(
            Long restaurantId,
            LocalDate fromDate,
            LocalDate toDate,
            List<OrderStatus> statuses);
}
