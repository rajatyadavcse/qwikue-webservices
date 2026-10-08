package com.kitchen.order.service;

import com.kitchen.order.dto.response.analytics.*;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.repository.OrderItemRepository;
import com.kitchen.order.repository.OrderRepository;
import com.kitchen.order.repository.projection.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdvancedAnalyticsServiceImpl implements IAdvancedAnalyticsService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final Executor analyticsTaskExecutor;

    private static final List<OrderStatus> DEFAULT_REVENUE_STATUSES = List.of(OrderStatus.COMPLETED);
    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 50;
    private static final int DEFAULT_MONTHS_LIMIT = 12;
    private static final int MAX_MONTHS_LIMIT = 36;

    public AdvancedAnalyticsServiceImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            @Qualifier("analyticsTaskExecutor") Executor analyticsTaskExecutor) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.analyticsTaskExecutor = analyticsTaskExecutor;
    }

    @Override
    @Transactional(readOnly = true)
    public AdvancedAnalyticsResponse getAdvancedAnalytics(
            Long restaurantId,
            LocalDate fromDate,
            LocalDate toDate,
            Integer monthsLimit,
            Integer topLimit,
            List<OrderStatus> statuses) {

        if (restaurantId == null) {
            throw new IllegalArgumentException("restaurantId cannot be null");
        }

        // 1. Parameter Normalization & Guardrails
        int effectiveTopLimit = (topLimit != null && topLimit > 0) ? Math.min(topLimit, MAX_TOP_LIMIT) : DEFAULT_TOP_LIMIT;
        int effectiveMonthsLimit = (monthsLimit != null && monthsLimit > 0) ? Math.min(monthsLimit, MAX_MONTHS_LIMIT) : DEFAULT_MONTHS_LIMIT;

        LocalDate effectiveFromDate = fromDate;
        LocalDate effectiveToDate = toDate;

        if (effectiveFromDate == null && effectiveToDate == null) {
            effectiveToDate = LocalDate.now();
            effectiveFromDate = effectiveToDate.minusDays(30);
        } else if (effectiveFromDate == null) {
            effectiveFromDate = effectiveToDate.minusDays(30);
        } else if (effectiveToDate == null) {
            effectiveToDate = LocalDate.now();
        }

        if (effectiveToDate.isBefore(effectiveFromDate)) {
            throw new IllegalArgumentException("toDate (" + effectiveToDate + ") cannot be before fromDate (" + effectiveFromDate + ")");
        }

        LocalDateTime start = effectiveFromDate.atStartOfDay();
        LocalDateTime end = effectiveToDate.plusDays(1).atStartOfDay();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().plusDays(1).atStartOfDay();

        // Fetch 1 extra baseline month prior for MoM growth calculation
        LocalDateTime monthlyFetchStart = LocalDate.now()
                .minusMonths(effectiveMonthsLimit)
                .withDayOfMonth(1)
                .minusMonths(1)
                .atStartOfDay();
        LocalDateTime monthlyEnd = LocalDate.now().plusDays(1).atStartOfDay();

        List<OrderStatus> targetStatuses = (statuses != null && !statuses.isEmpty()) ? statuses : DEFAULT_REVENUE_STATUSES;
        List<String> targetStatusNames = targetStatuses.stream().map(Enum::name).collect(Collectors.toList());

        // 2. Multithreaded Execution using CompletableFuture & Dedicated Thread Pool
        CompletableFuture<List<DailyRevenueProjection>> dailyFuture =
                CompletableFuture.supplyAsync(() -> orderRepository.getDailyRevenueTrends(restaurantId, targetStatusNames, start, end), analyticsTaskExecutor);

        CompletableFuture<List<MonthlyRevenueProjection>> monthlyFuture =
                CompletableFuture.supplyAsync(() -> orderRepository.getMonthlyRevenueTrends(restaurantId, targetStatusNames, monthlyFetchStart, monthlyEnd), analyticsTaskExecutor);

        CompletableFuture<List<TopItemProjection>> topTodayFuture =
                CompletableFuture.supplyAsync(() -> orderItemRepository.getTopOrderedItems(restaurantId, targetStatusNames, todayStart, todayEnd, effectiveTopLimit), analyticsTaskExecutor);

        CompletableFuture<List<TopItemProjection>> topRangeFuture =
                CompletableFuture.supplyAsync(() -> orderItemRepository.getTopOrderedItems(restaurantId, targetStatusNames, start, end, effectiveTopLimit), analyticsTaskExecutor);

        CompletableFuture<List<HourlyPeakProjection>> hourlyFuture =
                CompletableFuture.supplyAsync(() -> orderRepository.getHourlyPeakTrends(restaurantId, targetStatusNames, start, end), analyticsTaskExecutor);

        CompletableFuture<List<OrderSourceProjection>> channelFuture =
                CompletableFuture.supplyAsync(() -> orderRepository.getOrderSourceBreakdown(restaurantId, targetStatusNames, start, end), analyticsTaskExecutor);

        CompletableFuture.allOf(dailyFuture, monthlyFuture, topTodayFuture, topRangeFuture, hourlyFuture, channelFuture).join();

        // 3. Daily Revenue Trend Processing
        List<DailyRevenueProjection> dailyProjs = dailyFuture.join();
        List<DailyRevenueDTO> dailyList = new ArrayList<>();
        if (dailyProjs != null) {
            for (DailyRevenueProjection proj : dailyProjs) {
                BigDecimal revenue = proj.getRevenue() != null ? proj.getRevenue().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                Long count = proj.getOrderCount() != null ? proj.getOrderCount() : 0L;
                BigDecimal aov = (count > 0 && revenue.compareTo(BigDecimal.ZERO) > 0)
                        ? revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

                LocalDate date = proj.getDateStr() != null ? LocalDate.parse(proj.getDateStr()) : null;

                dailyList.add(DailyRevenueDTO.builder()
                        .date(date)
                        .revenue(revenue)
                        .orderCount(count)
                        .averageOrderValue(aov)
                        .build());
            }
        }

        // 4. Monthly Revenue & MoM Growth Processing
        List<MonthlyRevenueProjection> monthlyProjs = monthlyFuture.join();
        List<MonthlyRevenueDTO> monthlyList = new ArrayList<>();
        if (monthlyProjs != null && !monthlyProjs.isEmpty()) {
            BigDecimal prevRevenue = null;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

            for (MonthlyRevenueProjection proj : monthlyProjs) {
                Integer year = proj.getYearVal();
                Integer month = proj.getMonthVal();
                BigDecimal revenue = proj.getRevenue() != null ? proj.getRevenue().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                Long count = proj.getOrderCount() != null ? proj.getOrderCount() : 0L;

                BigDecimal growthRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                if (prevRevenue != null && prevRevenue.compareTo(BigDecimal.ZERO) > 0) {
                    growthRate = revenue.subtract(prevRevenue)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(prevRevenue, 2, RoundingMode.HALF_UP);
                }

                prevRevenue = revenue;

                LocalDate monthDate = LocalDate.of(year, month, 1);
                String yearMonthStr = monthDate.format(formatter);

                MonthlyRevenueDTO dto = MonthlyRevenueDTO.builder()
                        .year(year)
                        .month(month)
                        .yearMonth(yearMonthStr)
                        .revenue(revenue)
                        .orderCount(count)
                        .growthRatePercentage(growthRate)
                        .build();

                monthlyList.add(dto);
            }

            // Exclude baseline month if total returned exceeds effectiveMonthsLimit
            if (monthlyList.size() > effectiveMonthsLimit) {
                monthlyList = monthlyList.subList(monthlyList.size() - effectiveMonthsLimit, monthlyList.size());
            }
        }

        // 5. Top Items Processing (Today & Range)
        List<TopItemAnalyticsDTO> topTodayList = mapTopItems(topTodayFuture.join());
        List<TopItemAnalyticsDTO> topRangeList = mapTopItems(topRangeFuture.join());

        // 6. Hourly Peaks Processing
        List<HourlyPeakProjection> hourlyProjs = hourlyFuture.join();
        List<HourlyPeakDTO> hourlyList = new ArrayList<>();
        if (hourlyProjs != null) {
            for (HourlyPeakProjection proj : hourlyProjs) {
                BigDecimal revenue = proj.getRevenue() != null ? proj.getRevenue().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                Long count = proj.getOrderCount() != null ? proj.getOrderCount() : 0L;
                hourlyList.add(HourlyPeakDTO.builder()
                        .hourOfDay(proj.getHourOfDay())
                        .orderCount(count)
                        .revenue(revenue)
                        .build());
            }
        }

        // 7. Channel / Source Processing
        List<OrderSourceProjection> channelProjs = channelFuture.join();
        List<OrderSourceBreakdownDTO> channelList = new ArrayList<>();
        BigDecimal totalChannelRevenue = BigDecimal.ZERO;
        if (channelProjs != null) {
            for (OrderSourceProjection proj : channelProjs) {
                if (proj.getRevenue() != null) {
                    totalChannelRevenue = totalChannelRevenue.add(proj.getRevenue());
                }
            }
            for (OrderSourceProjection proj : channelProjs) {
                BigDecimal revenue = proj.getRevenue() != null ? proj.getRevenue().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                Long count = proj.getOrderCount() != null ? proj.getOrderCount() : 0L;
                BigDecimal percentage = (totalChannelRevenue.compareTo(BigDecimal.ZERO) > 0)
                        ? revenue.multiply(BigDecimal.valueOf(100)).divide(totalChannelRevenue, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

                channelList.add(OrderSourceBreakdownDTO.builder()
                        .orderedBy(proj.getOrderedBy())
                        .orderCount(count)
                        .revenue(revenue)
                        .percentage(percentage)
                        .build());
            }
        }

        return AdvancedAnalyticsResponse.builder()
                .restaurantId(restaurantId)
                .fromDate(effectiveFromDate)
                .toDate(effectiveToDate)
                .monthsLimit(effectiveMonthsLimit)
                .dailyTrends(dailyList)
                .monthlyTrends(monthlyList)
                .topItemsToday(topTodayList)
                .topItemsForRange(topRangeList)
                .hourlyPeaks(hourlyList)
                .orderSourceBreakdown(channelList)
                .build();
    }

    private List<TopItemAnalyticsDTO> mapTopItems(List<TopItemProjection> projs) {
        List<TopItemAnalyticsDTO> list = new ArrayList<>();
        if (projs != null) {
            for (TopItemProjection proj : projs) {
                BigDecimal revenue = proj.getTotalRevenue() != null ? proj.getTotalRevenue().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                Long qty = proj.getTotalQuantitySold() != null ? proj.getTotalQuantitySold() : 0L;
                Long count = proj.getOrderCount() != null ? proj.getOrderCount() : 0L;

                list.add(TopItemAnalyticsDTO.builder()
                        .menuId(proj.getMenuId())
                        .itemName(proj.getItemName())
                        .totalQuantitySold(qty)
                        .totalRevenue(revenue)
                        .orderCount(count)
                        .build());
            }
        }
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemSalesResponse getMenuItemSales(
            Long restaurantId,
            Long menuId,
            LocalDate fromDate,
            LocalDate toDate,
            List<OrderStatus> statuses) {

        if (restaurantId == null) {
            throw new IllegalArgumentException("restaurantId cannot be null");
        }
        if (menuId == null) {
            throw new IllegalArgumentException("menuId cannot be null");
        }

        LocalDate effectiveFromDate = fromDate;
        LocalDate effectiveToDate = toDate;

        if (effectiveFromDate == null && effectiveToDate == null) {
            effectiveToDate = LocalDate.now();
            effectiveFromDate = effectiveToDate.minusDays(30);
        } else if (effectiveFromDate == null) {
            effectiveFromDate = effectiveToDate.minusDays(30);
        } else if (effectiveToDate == null) {
            effectiveToDate = LocalDate.now();
        }

        if (effectiveToDate.isBefore(effectiveFromDate)) {
            throw new IllegalArgumentException("toDate (" + effectiveToDate + ") cannot be before fromDate (" + effectiveFromDate + ")");
        }

        LocalDateTime start = effectiveFromDate.atStartOfDay();
        LocalDateTime end = effectiveToDate.plusDays(1).atStartOfDay();

        List<OrderStatus> targetStatuses = (statuses != null && !statuses.isEmpty()) ? statuses : DEFAULT_REVENUE_STATUSES;
        List<String> targetStatusNames = targetStatuses.stream().map(Enum::name).collect(Collectors.toList());

        CompletableFuture<Optional<MenuItemSalesSummaryProjection>> summaryFuture =
                CompletableFuture.supplyAsync(() -> orderItemRepository.getMenuItemSalesSummary(
                        restaurantId, menuId, targetStatusNames, start, end), analyticsTaskExecutor);

        CompletableFuture<List<MenuItemDailySalesProjection>> dailyFuture =
                CompletableFuture.supplyAsync(() -> orderItemRepository.getMenuItemDailySales(
                        restaurantId, menuId, targetStatusNames, start, end), analyticsTaskExecutor);

        CompletableFuture.allOf(summaryFuture, dailyFuture).join();

        Optional<MenuItemSalesSummaryProjection> summaryOpt = summaryFuture.join();
        List<MenuItemDailySalesProjection> dailyProjs = dailyFuture.join();

        String itemName = null;
        Long totalQuantitySold = 0L;
        BigDecimal totalRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Long orderCount = 0L;

        if (summaryOpt != null && summaryOpt.isPresent()) {
            MenuItemSalesSummaryProjection summary = summaryOpt.get();
            itemName = summary.getItemName();
            totalQuantitySold = summary.getTotalQuantitySold() != null ? summary.getTotalQuantitySold() : 0L;
            totalRevenue = summary.getTotalRevenue() != null ? summary.getTotalRevenue().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            orderCount = summary.getOrderCount() != null ? summary.getOrderCount() : 0L;
        }

        BigDecimal avgPrice = (totalQuantitySold > 0 && totalRevenue.compareTo(BigDecimal.ZERO) > 0)
                ? totalRevenue.divide(BigDecimal.valueOf(totalQuantitySold), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<DailyItemSalesDTO> dailyTrends = new ArrayList<>();
        if (dailyProjs != null) {
            for (MenuItemDailySalesProjection proj : dailyProjs) {
                LocalDate date = proj.getDateStr() != null ? LocalDate.parse(proj.getDateStr()) : null;
                BigDecimal rev = proj.getRevenue() != null ? proj.getRevenue().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                Long qty = proj.getQuantitySold() != null ? proj.getQuantitySold() : 0L;
                Long count = proj.getOrderCount() != null ? proj.getOrderCount() : 0L;

                dailyTrends.add(DailyItemSalesDTO.builder()
                        .date(date)
                        .quantitySold(qty)
                        .revenue(rev)
                        .orderCount(count)
                        .build());
            }
        }

        return MenuItemSalesResponse.builder()
                .menuId(menuId)
                .restaurantId(restaurantId)
                .itemName(itemName)
                .fromDate(effectiveFromDate)
                .toDate(effectiveToDate)
                .totalQuantitySold(totalQuantitySold)
                .totalRevenue(totalRevenue)
                .orderCount(orderCount)
                .averageSellingPrice(avgPrice)
                .dailyTrends(dailyTrends)
                .build();
    }
}
