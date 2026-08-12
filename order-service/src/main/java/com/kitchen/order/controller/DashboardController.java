package com.kitchen.order.controller;

import com.kitchen.order.dto.response.dashboard.DashboardRevenueResponse;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.service.IDashboardAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/orders/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard Analytics", description = "Dashboard analytics and reporting endpoints")
public class DashboardController {

    private final IDashboardAnalyticsService dashboardAnalyticsService;

    @Operation(
            summary = "Get dashboard revenue and sales breakdown",
            description = "Returns aggregated revenue summary, breakdown by payment modes (CASH/ONLINE), sub-payment modes (UPI/CASH/CARD), " +
                          "order types (DINE_IN/TAKE_AWAY), and order status counts for a restaurant across a date range."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard revenue details fetched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid restaurantId or date range")
    })
    @GetMapping(value = "/revenue", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DashboardRevenueResponse> getDashboardRevenue(
            @Parameter(description = "Restaurant ID", required = true)
            @RequestParam Long restaurantId,

            @Parameter(description = "Start date (inclusive, yyyy-MM-dd, Asia/Kolkata). Defaults to today if omitted.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "End date (inclusive, yyyy-MM-dd, Asia/Kolkata). Defaults to today if omitted.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @Parameter(description = "Order statuses to include in revenue calculation (defaults to COMPLETED)")
            @RequestParam(required = false) List<OrderStatus> statuses) {

        DashboardRevenueResponse response = dashboardAnalyticsService.getDashboardRevenue(restaurantId, fromDate, toDate, statuses);
        return ResponseEntity.ok(response);
    }
}
