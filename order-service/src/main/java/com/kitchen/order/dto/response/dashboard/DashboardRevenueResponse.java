package com.kitchen.order.dto.response.dashboard;

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
public class DashboardRevenueResponse {
    private Long restaurantId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private RevenueSummaryDTO summary;
    private PaymentBreakdownDTO paymentBreakdown;

    @Builder.Default
    private List<OrderTypeBreakdownDTO> orderTypeBreakdown = new ArrayList<>();

    @Builder.Default
    private List<OrderStatusCountDTO> orderStatusBreakdown = new ArrayList<>();
}
