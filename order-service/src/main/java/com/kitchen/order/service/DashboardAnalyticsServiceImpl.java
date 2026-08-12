package com.kitchen.order.service;

import com.kitchen.order.dto.response.dashboard.*;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.enums.OrderType;
import com.kitchen.order.repository.OrderRepository;
import com.kitchen.order.repository.projection.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardAnalyticsServiceImpl implements IDashboardAnalyticsService {

    private final OrderRepository orderRepository;

    private static final List<OrderStatus> DEFAULT_REVENUE_STATUSES = List.of(
            OrderStatus.COMPLETED
    );

    @Override
    @Transactional(readOnly = true)
    public DashboardRevenueResponse getDashboardRevenue(
            Long restaurantId,
            LocalDate fromDate,
            LocalDate toDate,
            List<OrderStatus> statuses) {

        if (restaurantId == null) {
            throw new IllegalArgumentException("restaurantId cannot be null");
        }

        // Date normalizations: default to current date if both omitted
        LocalDate effectiveFromDate = fromDate;
        LocalDate effectiveToDate = toDate;

        if (effectiveFromDate == null && effectiveToDate == null) {
            effectiveFromDate = LocalDate.now();
            effectiveToDate = LocalDate.now();
        } else if (effectiveFromDate == null) {
            effectiveFromDate = effectiveToDate;
        } else if (effectiveToDate == null) {
            effectiveToDate = effectiveFromDate;
        }

        if (effectiveToDate.isBefore(effectiveFromDate)) {
            throw new IllegalArgumentException("toDate (" + effectiveToDate + ") cannot be before fromDate (" + effectiveFromDate + ")");
        }

        LocalDateTime start = effectiveFromDate.atStartOfDay();
        LocalDateTime end = effectiveToDate.plusDays(1).atStartOfDay();

        List<OrderStatus> targetStatuses = (statuses != null && !statuses.isEmpty())
                ? statuses
                : DEFAULT_REVENUE_STATUSES;

        // 1. Overall Summary
        RevenueSummaryProjection summaryProj = orderRepository.getRevenueSummary(restaurantId, targetStatuses, start, end);
        BigDecimal totalRevenue = (summaryProj != null && summaryProj.getTotalRevenue() != null)
                ? summaryProj.getTotalRevenue().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal netSubTotal = (summaryProj != null && summaryProj.getNetSubTotal() != null)
                ? summaryProj.getNetSubTotal().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTax = (summaryProj != null && summaryProj.getTotalTax() != null)
                ? summaryProj.getTotalTax().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalServiceCharge = (summaryProj != null && summaryProj.getTotalServiceCharge() != null)
                ? summaryProj.getTotalServiceCharge().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDiscount = (summaryProj != null && summaryProj.getTotalDiscount() != null)
                ? summaryProj.getTotalDiscount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Long totalOrders = (summaryProj != null && summaryProj.getTotalOrders() != null)
                ? summaryProj.getTotalOrders()
                : 0L;

        BigDecimal averageOrderValue = (totalOrders > 0 && totalRevenue.compareTo(BigDecimal.ZERO) > 0)
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        RevenueSummaryDTO summaryDTO = RevenueSummaryDTO.builder()
                .totalRevenue(totalRevenue)
                .netSubTotal(netSubTotal)
                .totalTax(totalTax)
                .totalServiceCharge(totalServiceCharge)
                .totalDiscount(totalDiscount)
                .totalOrders(totalOrders)
                .averageOrderValue(averageOrderValue)
                .build();

        // 2. Payment Mode Breakdown
        List<PaymentModeRevenueProjection> paymentModeProjs = orderRepository.getRevenueByPaymentMode(restaurantId, targetStatuses, start, end);
        List<PaymentModeBreakdownDTO> paymentModeList = new ArrayList<>();
        if (paymentModeProjs != null) {
            for (PaymentModeRevenueProjection proj : paymentModeProjs) {
                BigDecimal amount = proj.getAmount() != null ? proj.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                Long count = proj.getOrderCount() != null ? proj.getOrderCount() : 0L;
                BigDecimal percentage = calculatePercentage(amount, totalRevenue);
                paymentModeList.add(PaymentModeBreakdownDTO.builder()
                        .paymentMode(proj.getPaymentMode())
                        .amount(amount)
                        .orderCount(count)
                        .percentage(percentage)
                        .build());
            }
        }

        // 3. SubPaymentMode Breakdown
        List<SubPaymentModeRevenueProjection> subPaymentModeProjs = orderRepository.getRevenueBySubPaymentMode(restaurantId, targetStatuses, start, end);
        List<SubPaymentModeBreakdownDTO> subPaymentModeList = new ArrayList<>();
        if (subPaymentModeProjs != null) {
            for (SubPaymentModeRevenueProjection proj : subPaymentModeProjs) {
                BigDecimal amount = proj.getAmount() != null ? proj.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                Long count = proj.getOrderCount() != null ? proj.getOrderCount() : 0L;
                BigDecimal percentage = calculatePercentage(amount, totalRevenue);
                subPaymentModeList.add(SubPaymentModeBreakdownDTO.builder()
                        .subPaymentMode(proj.getSubPaymentMode())
                        .amount(amount)
                        .orderCount(count)
                        .percentage(percentage)
                        .build());
            }
        }

        PaymentBreakdownDTO paymentBreakdown = PaymentBreakdownDTO.builder()
                .byPaymentMode(paymentModeList)
                .bySubPaymentMode(subPaymentModeList)
                .build();

        // 4. Order Type Breakdown (DINE_IN vs TAKE_AWAY)
        List<OrderTypeRevenueProjection> orderTypeProjs = orderRepository.getRevenueByOrderType(restaurantId, targetStatuses, start, end);
        List<OrderTypeBreakdownDTO> orderTypeList = new ArrayList<>();
        if (orderTypeProjs != null) {
            for (OrderTypeRevenueProjection proj : orderTypeProjs) {
                BigDecimal amount = proj.getAmount() != null ? proj.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                Long count = proj.getOrderCount() != null ? proj.getOrderCount() : 0L;
                BigDecimal percentage = calculatePercentage(amount, totalRevenue);
                orderTypeList.add(OrderTypeBreakdownDTO.builder()
                        .orderType(proj.getOrderType())
                        .amount(amount)
                        .orderCount(count)
                        .percentage(percentage)
                        .build());
            }
        }

        // 5. Order Status Counts (all statuses for this date range)
        List<OrderStatusCountProjection> statusCountProjs = orderRepository.getOrderStatusCounts(restaurantId, start, end);
        List<OrderStatusCountDTO> statusCountList = new ArrayList<>();
        if (statusCountProjs != null) {
            for (OrderStatusCountProjection proj : statusCountProjs) {
                statusCountList.add(OrderStatusCountDTO.builder()
                        .status(proj.getStatus())
                        .orderCount(proj.getOrderCount() != null ? proj.getOrderCount() : 0L)
                        .build());
            }
        }

        return DashboardRevenueResponse.builder()
                .restaurantId(restaurantId)
                .fromDate(effectiveFromDate)
                .toDate(effectiveToDate)
                .summary(summaryDTO)
                .paymentBreakdown(paymentBreakdown)
                .orderTypeBreakdown(orderTypeList)
                .orderStatusBreakdown(statusCountList)
                .build();
    }

    private BigDecimal calculatePercentage(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0 || part == null || part.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP);
    }
}
