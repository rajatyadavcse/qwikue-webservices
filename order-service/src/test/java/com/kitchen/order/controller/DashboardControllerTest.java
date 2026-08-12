package com.kitchen.order.controller;

import com.kitchen.order.dto.response.dashboard.DashboardRevenueResponse;
import com.kitchen.order.dto.response.dashboard.RevenueSummaryDTO;
import com.kitchen.order.service.IDashboardAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private IDashboardAnalyticsService dashboardAnalyticsService;

    @InjectMocks
    private DashboardController dashboardController;

    @Test
    @DisplayName("getDashboardRevenue - Returns 200 OK with response body")
    void testGetDashboardRevenue() {
        Long restaurantId = 1L;
        LocalDate fromDate = LocalDate.of(2026, 8, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 12);

        DashboardRevenueResponse mockResponse = DashboardRevenueResponse.builder()
                .restaurantId(restaurantId)
                .fromDate(fromDate)
                .toDate(toDate)
                .summary(RevenueSummaryDTO.builder()
                        .totalRevenue(new BigDecimal("5000.00"))
                        .totalOrders(10L)
                        .build())
                .build();

        when(dashboardAnalyticsService.getDashboardRevenue(eq(restaurantId), eq(fromDate), eq(toDate), any()))
                .thenReturn(mockResponse);

        ResponseEntity<DashboardRevenueResponse> responseEntity = dashboardController.getDashboardRevenue(
                restaurantId, fromDate, toDate, null);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(restaurantId, responseEntity.getBody().getRestaurantId());
        assertEquals(new BigDecimal("5000.00"), responseEntity.getBody().getSummary().getTotalRevenue());
    }
}
