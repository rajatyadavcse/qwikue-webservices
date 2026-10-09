package com.kitchen.order.controller;

import com.kitchen.order.dto.response.analytics.AdvancedAnalyticsResponse;
import com.kitchen.order.dto.response.analytics.MenuItemSalesResponse;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.service.IAdvancedAnalyticsService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/orders/analytics")
@RequiredArgsConstructor
@Tag(name = "Advanced Analytics", description = "Advanced historical trends, graphical analytics, and menu performance endpoints")
public class AdvancedAnalyticsController {

    private final IAdvancedAnalyticsService advancedAnalyticsService;

    @Operation(
            summary = "Get advanced analytics and historical trends",
            description = "Returns Day-on-Day revenue trends, Month-on-Month revenue & growth rate, " +
                          "Top ordered items (Today & Date Range), 24-hour peak hour heatmaps, and order channel breakdowns."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Advanced analytics details fetched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid restaurantId or date range parameters")
    })
    @GetMapping(value = "/advanced", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdvancedAnalyticsResponse> getAdvancedAnalytics(
            @Parameter(description = "Restaurant ID", required = true)
            @RequestParam Long restaurantId,

            @Parameter(description = "Start date for daily trends (inclusive, yyyy-MM-dd). Defaults to 30 days ago if omitted.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "End date for daily trends (inclusive, yyyy-MM-dd). Defaults to today if omitted.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @Parameter(description = "Number of months to include in Month-on-Month trends (default 12, max 36).")
            @RequestParam(required = false, defaultValue = "12") Integer monthsLimit,

            @Parameter(description = "Number of top ordered items to return (default 10, max 50).")
            @RequestParam(required = false, defaultValue = "10") Integer topLimit,

            @Parameter(description = "Order statuses to include in revenue calculation (defaults to COMPLETED).")
            @RequestParam(required = false) List<OrderStatus> statuses) {

        AdvancedAnalyticsResponse response = advancedAnalyticsService.getAdvancedAnalytics(
                restaurantId, fromDate, toDate, monthsLimit, topLimit, statuses);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get sales figures and daily trends for a specific menu item",
            description = "Returns aggregated quantity sold, revenue, order count, and day-by-day sales figures for a specific menu item within a date range."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Menu item sales analytics fetched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid restaurantId, menuId, or date range parameters")
    })
    @GetMapping(value = "/items/{menuId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MenuItemSalesResponse> getMenuItemSales(
            @Parameter(description = "Menu Item ID", required = true)
            @PathVariable Long menuId,

            @Parameter(description = "Restaurant ID", required = true)
            @RequestParam Long restaurantId,

            @Parameter(description = "Start date (inclusive, yyyy-MM-dd). Defaults to 30 days ago if omitted.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "End date (inclusive, yyyy-MM-dd). Defaults to today if omitted.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @Parameter(description = "Order statuses to include in calculation (defaults to COMPLETED).")
            @RequestParam(required = false) List<OrderStatus> statuses) {

        MenuItemSalesResponse response = advancedAnalyticsService.getMenuItemSales(
                restaurantId, menuId, fromDate, toDate, statuses);
        return ResponseEntity.ok(response);
    }
}
