package com.kitchen.order.service;

import com.kitchen.order.dto.response.dashboard.DashboardRevenueResponse;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.enums.OrderType;
import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.repository.OrderRepository;
import com.kitchen.order.repository.projection.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAnalyticsServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private DashboardAnalyticsServiceImpl dashboardAnalyticsService;

    @Test
    @DisplayName("getDashboardRevenue - Successful aggregation with full breakdown")
    void testGetDashboardRevenue_Success() {
        Long restaurantId = 1L;
        LocalDate fromDate = LocalDate.of(2026, 8, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 12);

        // Mock summary projection
        RevenueSummaryProjection summaryMock = mock(RevenueSummaryProjection.class);
        when(summaryMock.getTotalRevenue()).thenReturn(new BigDecimal("1000.00"));
        when(summaryMock.getNetSubTotal()).thenReturn(new BigDecimal("900.00"));
        when(summaryMock.getTotalTax()).thenReturn(new BigDecimal("50.00"));
        when(summaryMock.getTotalServiceCharge()).thenReturn(new BigDecimal("50.00"));
        when(summaryMock.getTotalDiscount()).thenReturn(new BigDecimal("20.00"));
        when(summaryMock.getTotalOrders()).thenReturn(4L);
        when(summaryMock.getAveragePrepTime()).thenReturn(15.5);

        when(orderRepository.getRevenueSummary(eq(restaurantId), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(summaryMock);

        // Mock payment mode projections
        PaymentModeRevenueProjection cashMock = mock(PaymentModeRevenueProjection.class);
        when(cashMock.getPaymentMode()).thenReturn(PaymentMode.CASH);
        when(cashMock.getAmount()).thenReturn(new BigDecimal("600.00"));
        when(cashMock.getOrderCount()).thenReturn(2L);

        PaymentModeRevenueProjection onlineMock = mock(PaymentModeRevenueProjection.class);
        when(onlineMock.getPaymentMode()).thenReturn(PaymentMode.ONLINE);
        when(onlineMock.getAmount()).thenReturn(new BigDecimal("400.00"));
        when(onlineMock.getOrderCount()).thenReturn(2L);

        when(orderRepository.getRevenueByPaymentMode(eq(restaurantId), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(cashMock, onlineMock));

        // Mock sub-payment mode projections
        SubPaymentModeRevenueProjection upiMock = mock(SubPaymentModeRevenueProjection.class);
        when(upiMock.getSubPaymentMode()).thenReturn("UPI");
        when(upiMock.getAmount()).thenReturn(new BigDecimal("300.00"));
        when(upiMock.getOrderCount()).thenReturn(1L);

        SubPaymentModeRevenueProjection cashSubMock = mock(SubPaymentModeRevenueProjection.class);
        when(cashSubMock.getSubPaymentMode()).thenReturn("CASH");
        when(cashSubMock.getAmount()).thenReturn(new BigDecimal("300.00"));
        when(cashSubMock.getOrderCount()).thenReturn(1L);

        when(orderRepository.getRevenueBySubPaymentMode(eq(restaurantId), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(upiMock, cashSubMock));

        // Mock order type projections
        OrderTypeRevenueProjection dineInMock = mock(OrderTypeRevenueProjection.class);
        when(dineInMock.getOrderType()).thenReturn(OrderType.DINE_IN);
        when(dineInMock.getAmount()).thenReturn(new BigDecimal("700.00"));
        when(dineInMock.getOrderCount()).thenReturn(3L);

        OrderTypeRevenueProjection takeAwayMock = mock(OrderTypeRevenueProjection.class);
        when(takeAwayMock.getOrderType()).thenReturn(OrderType.TAKE_AWAY);
        when(takeAwayMock.getAmount()).thenReturn(new BigDecimal("300.00"));
        when(takeAwayMock.getOrderCount()).thenReturn(1L);

        when(orderRepository.getRevenueByOrderType(eq(restaurantId), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dineInMock, takeAwayMock));

        // Mock order status counts
        OrderStatusCountProjection completedStatusMock = mock(OrderStatusCountProjection.class);
        when(completedStatusMock.getStatus()).thenReturn(OrderStatus.COMPLETED);
        when(completedStatusMock.getOrderCount()).thenReturn(4L);

        OrderStatusCountProjection cancelledStatusMock = mock(OrderStatusCountProjection.class);
        when(cancelledStatusMock.getStatus()).thenReturn(OrderStatus.CANCELLED);
        when(cancelledStatusMock.getOrderCount()).thenReturn(2L);

        OrderStatusCountProjection rejectedStatusMock = mock(OrderStatusCountProjection.class);
        when(rejectedStatusMock.getStatus()).thenReturn(OrderStatus.REJECTED);
        when(rejectedStatusMock.getOrderCount()).thenReturn(1L);

        OrderStatusCountProjection pendingStatusMock = mock(OrderStatusCountProjection.class);
        when(pendingStatusMock.getStatus()).thenReturn(OrderStatus.PENDING);
        when(pendingStatusMock.getOrderCount()).thenReturn(3L);

        OrderStatusCountProjection preparingStatusMock = mock(OrderStatusCountProjection.class);
        when(preparingStatusMock.getStatus()).thenReturn(OrderStatus.PREPARING);
        when(preparingStatusMock.getOrderCount()).thenReturn(5L);

        OrderStatusCountProjection readyStatusMock = mock(OrderStatusCountProjection.class);
        when(readyStatusMock.getStatus()).thenReturn(OrderStatus.READY);
        when(readyStatusMock.getOrderCount()).thenReturn(2L);

        when(orderRepository.getOrderStatusCounts(eq(restaurantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(completedStatusMock, cancelledStatusMock, rejectedStatusMock, pendingStatusMock, preparingStatusMock, readyStatusMock));

        // Execute
        DashboardRevenueResponse response = dashboardAnalyticsService.getDashboardRevenue(restaurantId, fromDate, toDate, null);

        // Assertions
        assertNotNull(response);
        assertEquals(restaurantId, response.getRestaurantId());
        assertEquals(fromDate, response.getFromDate());
        assertEquals(toDate, response.getToDate());

        // Summary assertions
        assertNotNull(response.getSummary());
        assertEquals(new BigDecimal("1000.00"), response.getSummary().getTotalRevenue());
        assertEquals(new BigDecimal("900.00"), response.getSummary().getNetSubTotal());
        assertEquals(new BigDecimal("50.00"), response.getSummary().getTotalTax());
        assertEquals(new BigDecimal("50.00"), response.getSummary().getTotalServiceCharge());
        assertEquals(new BigDecimal("20.00"), response.getSummary().getTotalDiscount());
        assertEquals(4L, response.getSummary().getTotalOrders());
        assertEquals(new BigDecimal("250.00"), response.getSummary().getAverageOrderValue());
        assertEquals(2L, response.getSummary().getCancelledOrdersCount());
        assertEquals(1L, response.getSummary().getRejectedOrdersCount());
        assertEquals(10L, response.getSummary().getActiveOrdersCount());
        assertEquals(15.5, response.getSummary().getAveragePrepTime());

        // Payment mode assertions
        assertNotNull(response.getPaymentBreakdown());
        assertEquals(2, response.getPaymentBreakdown().getByPaymentMode().size());
        assertEquals(PaymentMode.CASH, response.getPaymentBreakdown().getByPaymentMode().get(0).getPaymentMode());
        assertEquals(new BigDecimal("60.00"), response.getPaymentBreakdown().getByPaymentMode().get(0).getPercentage());
        assertEquals(PaymentMode.ONLINE, response.getPaymentBreakdown().getByPaymentMode().get(1).getPaymentMode());
        assertEquals(new BigDecimal("40.00"), response.getPaymentBreakdown().getByPaymentMode().get(1).getPercentage());

        // Sub-payment mode assertions
        assertEquals(2, response.getPaymentBreakdown().getBySubPaymentMode().size());
        assertEquals("UPI", response.getPaymentBreakdown().getBySubPaymentMode().get(0).getSubPaymentMode());
        assertEquals(new BigDecimal("30.00"), response.getPaymentBreakdown().getBySubPaymentMode().get(0).getPercentage());

        // Order type assertions
        assertEquals(2, response.getOrderTypeBreakdown().size());
        assertEquals(OrderType.DINE_IN, response.getOrderTypeBreakdown().get(0).getOrderType());
        assertEquals(new BigDecimal("70.00"), response.getOrderTypeBreakdown().get(0).getPercentage());
        assertEquals(OrderType.TAKE_AWAY, response.getOrderTypeBreakdown().get(1).getOrderType());
        assertEquals(new BigDecimal("30.00"), response.getOrderTypeBreakdown().get(1).getPercentage());

        // Order status count assertions
        assertEquals(6, response.getOrderStatusBreakdown().size());
        assertEquals(OrderStatus.COMPLETED, response.getOrderStatusBreakdown().get(0).getStatus());
        assertEquals(4L, response.getOrderStatusBreakdown().get(0).getOrderCount());
    }

    @Test
    @DisplayName("getDashboardRevenue - Null dates default to current date")
    void testGetDashboardRevenue_DefaultDates() {
        Long restaurantId = 2L;

        RevenueSummaryProjection summaryMock = mock(RevenueSummaryProjection.class);
        when(summaryMock.getTotalRevenue()).thenReturn(null);
        when(summaryMock.getNetSubTotal()).thenReturn(null);
        when(summaryMock.getTotalTax()).thenReturn(null);
        when(summaryMock.getTotalServiceCharge()).thenReturn(null);
        when(summaryMock.getTotalDiscount()).thenReturn(null);
        when(summaryMock.getTotalOrders()).thenReturn(null);

        when(orderRepository.getRevenueSummary(eq(restaurantId), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(summaryMock);

        DashboardRevenueResponse response = dashboardAnalyticsService.getDashboardRevenue(restaurantId, null, null, null);

        assertNotNull(response);
        assertEquals(LocalDate.now(), response.getFromDate());
        assertEquals(LocalDate.now(), response.getToDate());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getSummary().getTotalRevenue());
        assertEquals(0L, response.getSummary().getTotalOrders());
        assertEquals(BigDecimal.ZERO.setScale(2), response.getSummary().getAverageOrderValue());
    }

    @Test
    @DisplayName("getDashboardRevenue - Invalid restaurantId or invalid date range throws IllegalArgumentException")
    void testGetDashboardRevenue_ValidationErrors() {
        assertThrows(IllegalArgumentException.class, () ->
                dashboardAnalyticsService.getDashboardRevenue(null, LocalDate.now(), LocalDate.now(), null));

        assertThrows(IllegalArgumentException.class, () ->
                dashboardAnalyticsService.getDashboardRevenue(1L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 5), null));
    }
}
