package com.kitchen.order.service;

import com.kitchen.order.dto.response.analytics.AdvancedAnalyticsResponse;
import com.kitchen.order.dto.response.analytics.MenuItemSalesResponse;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.repository.OrderItemRepository;
import com.kitchen.order.repository.OrderRepository;
import com.kitchen.order.repository.projection.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvancedAnalyticsServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    private AdvancedAnalyticsServiceImpl advancedAnalyticsService;

    @BeforeEach
    void setUp() {
        advancedAnalyticsService = new AdvancedAnalyticsServiceImpl(
                orderRepository,
                orderItemRepository,
                Executors.newFixedThreadPool(4)
        );
    }

    @Test
    void testGetAdvancedAnalytics_Success() {
        Long restaurantId = 1L;
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 26);

        DailyRevenueProjection dailyProj = new DailyRevenueProjection() {
            @Override
            public String getDateStr() { return "2026-09-15"; }
            @Override
            public BigDecimal getRevenue() { return new BigDecimal("1500.00"); }
            @Override
            public Long getOrderCount() { return 5L; }
        };

        MonthlyRevenueProjection month1 = new MonthlyRevenueProjection() {
            @Override
            public Integer getYearVal() { return 2026; }
            @Override
            public Integer getMonthVal() { return 8; }
            @Override
            public BigDecimal getRevenue() { return new BigDecimal("10000.00"); }
            @Override
            public Long getOrderCount() { return 40L; }
        };

        MonthlyRevenueProjection month2 = new MonthlyRevenueProjection() {
            @Override
            public Integer getYearVal() { return 2026; }
            @Override
            public Integer getMonthVal() { return 9; }
            @Override
            public BigDecimal getRevenue() { return new BigDecimal("15000.00"); }
            @Override
            public Long getOrderCount() { return 60L; }
        };

        TopItemProjection topItem = new TopItemProjection() {
            @Override
            public Long getMenuId() { return 101L; }
            @Override
            public String getItemName() { return "Paneer Butter Masala"; }
            @Override
            public Long getTotalQuantitySold() { return 30L; }
            @Override
            public BigDecimal getTotalRevenue() { return new BigDecimal("6000.00"); }
            @Override
            public Long getOrderCount() { return 25L; }
        };

        HourlyPeakProjection hourlyProj = new HourlyPeakProjection() {
            @Override
            public Integer getHourOfDay() { return 20; }
            @Override
            public Long getOrderCount() { return 15L; }
            @Override
            public BigDecimal getRevenue() { return new BigDecimal("4500.00"); }
        };

        OrderSourceProjection sourceProj = new OrderSourceProjection() {
            @Override
            public String getOrderedBy() { return "CUSTOMER"; }
            @Override
            public Long getOrderCount() { return 50L; }
            @Override
            public BigDecimal getRevenue() { return new BigDecimal("15000.00"); }
        };

        when(orderRepository.getDailyRevenueTrends(anyLong(), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyProj));

        when(orderRepository.getMonthlyRevenueTrends(anyLong(), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(month1, month2));

        when(orderItemRepository.getTopOrderedItems(anyLong(), anyList(), any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(topItem));

        when(orderRepository.getHourlyPeakTrends(anyLong(), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(hourlyProj));

        when(orderRepository.getOrderSourceBreakdown(anyLong(), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(sourceProj));

        AdvancedAnalyticsResponse response = advancedAnalyticsService.getAdvancedAnalytics(
                restaurantId, fromDate, toDate, 12, 10, List.of(OrderStatus.COMPLETED));

        assertNotNull(response);
        assertEquals(restaurantId, response.getRestaurantId());
        assertEquals(fromDate, response.getFromDate());
        assertEquals(toDate, response.getToDate());

        // Daily assertions
        assertEquals(1, response.getDailyTrends().size());
        assertEquals(new BigDecimal("1500.00"), response.getDailyTrends().get(0).getRevenue());
        assertEquals(new BigDecimal("300.00"), response.getDailyTrends().get(0).getAverageOrderValue());

        // Monthly MoM assertions (50% growth rate from 10k to 15k)
        assertFalse(response.getMonthlyTrends().isEmpty());
        assertEquals(new BigDecimal("50.00"), response.getMonthlyTrends().get(response.getMonthlyTrends().size() - 1).getGrowthRatePercentage());

        // Top items assertions
        assertEquals(1, response.getTopItemsToday().size());
        assertEquals("Paneer Butter Masala", response.getTopItemsToday().get(0).getItemName());

        // Hourly peaks assertions
        assertEquals(1, response.getHourlyPeaks().size());
        assertEquals(20, response.getHourlyPeaks().get(0).getHourOfDay());

        // Channel breakdown assertions
        assertEquals(1, response.getOrderSourceBreakdown().size());
        assertEquals(new BigDecimal("100.00"), response.getOrderSourceBreakdown().get(0).getPercentage());
    }

    @Test
    void testGetAdvancedAnalytics_NullRestaurantId_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                advancedAnalyticsService.getAdvancedAnalytics(null, null, null, 12, 10, null)
        );
    }

    @Test
    void testGetMenuItemSales_Success() {
        Long restaurantId = 1L;
        Long menuId = 101L;
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 26);

        MenuItemSalesSummaryProjection summaryProj = new MenuItemSalesSummaryProjection() {
            @Override
            public Long getMenuId() { return menuId; }
            @Override
            public String getItemName() { return "Paneer Butter Masala"; }
            @Override
            public Long getTotalQuantitySold() { return 20L; }
            @Override
            public BigDecimal getTotalRevenue() { return new BigDecimal("4000.00"); }
            @Override
            public Long getOrderCount() { return 15L; }
        };

        MenuItemDailySalesProjection dailyProj = new MenuItemDailySalesProjection() {
            @Override
            public String getDateStr() { return "2026-09-10"; }
            @Override
            public Long getQuantitySold() { return 5L; }
            @Override
            public BigDecimal getRevenue() { return new BigDecimal("1000.00"); }
            @Override
            public Long getOrderCount() { return 4L; }
        };

        when(orderItemRepository.getMenuItemSalesSummary(eq(restaurantId), eq(menuId), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(summaryProj));
        when(orderItemRepository.getMenuItemDailySales(eq(restaurantId), eq(menuId), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyProj));

        MenuItemSalesResponse response = advancedAnalyticsService.getMenuItemSales(
                restaurantId, menuId, fromDate, toDate, null);

        assertNotNull(response);
        assertEquals(menuId, response.getMenuId());
        assertEquals(restaurantId, response.getRestaurantId());
        assertEquals("Paneer Butter Masala", response.getItemName());
        assertEquals(20L, response.getTotalQuantitySold());
        assertEquals(new BigDecimal("4000.00"), response.getTotalRevenue());
        assertEquals(15L, response.getOrderCount());
        assertEquals(new BigDecimal("200.00"), response.getAverageSellingPrice());
        assertEquals(1, response.getDailyTrends().size());
        assertEquals(LocalDate.of(2026, 9, 10), response.getDailyTrends().get(0).getDate());
        assertEquals(5L, response.getDailyTrends().get(0).getQuantitySold());
        assertEquals(new BigDecimal("1000.00"), response.getDailyTrends().get(0).getRevenue());
        assertEquals(4L, response.getDailyTrends().get(0).getOrderCount());
    }

    @Test
    void testGetMenuItemSales_NoOrdersFound() {
        Long restaurantId = 1L;
        Long menuId = 999L;
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 26);

        when(orderItemRepository.getMenuItemSalesSummary(eq(restaurantId), eq(menuId), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(orderItemRepository.getMenuItemDailySales(eq(restaurantId), eq(menuId), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        MenuItemSalesResponse response = advancedAnalyticsService.getMenuItemSales(
                restaurantId, menuId, fromDate, toDate, null);

        assertNotNull(response);
        assertEquals(menuId, response.getMenuId());
        assertEquals(restaurantId, response.getRestaurantId());
        assertNull(response.getItemName());
        assertEquals(0L, response.getTotalQuantitySold());
        assertEquals(new BigDecimal("0.00"), response.getTotalRevenue());
        assertEquals(0L, response.getOrderCount());
        assertEquals(new BigDecimal("0.00"), response.getAverageSellingPrice());
        assertTrue(response.getDailyTrends().isEmpty());
    }

    @Test
    void testGetMenuItemSales_NullValidations() {
        assertThrows(IllegalArgumentException.class, () ->
                advancedAnalyticsService.getMenuItemSales(null, 101L, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                advancedAnalyticsService.getMenuItemSales(1L, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                advancedAnalyticsService.getMenuItemSales(1L, 101L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1), null));
    }
}
