package com.kitchen.order.controller;

import com.kitchen.order.dto.response.analytics.AdvancedAnalyticsResponse;
import com.kitchen.order.dto.response.analytics.DailyItemSalesDTO;
import com.kitchen.order.dto.response.analytics.MenuItemSalesResponse;
import com.kitchen.order.service.IAdvancedAnalyticsService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvancedAnalyticsControllerTest {

    @Mock
    private IAdvancedAnalyticsService advancedAnalyticsService;

    @InjectMocks
    private AdvancedAnalyticsController advancedAnalyticsController;

    @Test
    @DisplayName("getAdvancedAnalytics - Returns 200 OK with response body")
    void testGetAdvancedAnalytics() {
        Long restaurantId = 1L;
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 26);

        AdvancedAnalyticsResponse mockResponse = AdvancedAnalyticsResponse.builder()
                .restaurantId(restaurantId)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        when(advancedAnalyticsService.getAdvancedAnalytics(eq(restaurantId), eq(fromDate), eq(toDate), any(), any(), any()))
                .thenReturn(mockResponse);

        ResponseEntity<AdvancedAnalyticsResponse> responseEntity = advancedAnalyticsController.getAdvancedAnalytics(
                restaurantId, fromDate, toDate, 12, 10, null);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(restaurantId, responseEntity.getBody().getRestaurantId());
    }

    @Test
    @DisplayName("getMenuItemSales - Returns 200 OK with MenuItemSalesResponse")
    void testGetMenuItemSales() {
        Long restaurantId = 1L;
        Long menuId = 101L;
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 26);

        MenuItemSalesResponse mockResponse = MenuItemSalesResponse.builder()
                .menuId(menuId)
                .restaurantId(restaurantId)
                .itemName("Paneer Butter Masala")
                .fromDate(fromDate)
                .toDate(toDate)
                .totalQuantitySold(20L)
                .totalRevenue(new BigDecimal("4000.00"))
                .orderCount(15L)
                .averageSellingPrice(new BigDecimal("200.00"))
                .dailyTrends(List.of(DailyItemSalesDTO.builder()
                        .date(LocalDate.of(2026, 9, 10))
                        .quantitySold(5L)
                        .revenue(new BigDecimal("1000.00"))
                        .orderCount(4L)
                        .build()))
                .build();

        when(advancedAnalyticsService.getMenuItemSales(eq(restaurantId), eq(menuId), eq(fromDate), eq(toDate), any()))
                .thenReturn(mockResponse);

        ResponseEntity<MenuItemSalesResponse> responseEntity = advancedAnalyticsController.getMenuItemSales(
                menuId, restaurantId, fromDate, toDate, null);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(menuId, responseEntity.getBody().getMenuId());
        assertEquals(restaurantId, responseEntity.getBody().getRestaurantId());
        assertEquals("Paneer Butter Masala", responseEntity.getBody().getItemName());
        assertEquals(20L, responseEntity.getBody().getTotalQuantitySold());
        assertEquals(new BigDecimal("4000.00"), responseEntity.getBody().getTotalRevenue());
        assertEquals(1, responseEntity.getBody().getDailyTrends().size());
    }
}
