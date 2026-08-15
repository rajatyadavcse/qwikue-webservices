package com.kitchen.order.repository;

import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.enums.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.kitchen.order.repository.projection.OrderStatusCountProjection;
import com.kitchen.order.repository.projection.OrderTypeRevenueProjection;
import com.kitchen.order.repository.projection.PaymentModeRevenueProjection;
import com.kitchen.order.repository.projection.RevenueSummaryProjection;
import com.kitchen.order.repository.projection.SubPaymentModeRevenueProjection;

@Repository
public interface OrderRepository extends JpaRepository<OrderDAO, Long> {

    /** Fetch all orders for a restaurant, paginated. */
    Page<OrderDAO> findByRestaurantId(Long restaurantId, Pageable pageable);

    /** Fetch all orders for a restaurant excluding a specific status, paginated. */
    Page<OrderDAO> findByRestaurantIdAndStatusNot(Long restaurantId, OrderStatus status, Pageable pageable);

    /** Fetch orders for a restaurant filtered by status, paginated. */
    Page<OrderDAO> findByRestaurantIdAndStatus(Long restaurantId, OrderStatus status, Pageable pageable);

    /** Fetch active kitchen orders (non-terminal statuses) for a specific restaurant. */
    List<OrderDAO> findByRestaurantIdAndStatusIn(Long restaurantId, List<OrderStatus> statuses);

    /** Fetch online orders that are pending payment and created before the threshold time. */
    List<OrderDAO> findByPaymentModeAndPaymentStatusAndCreatedAtBefore(
            PaymentMode paymentMode, PaymentStatus paymentStatus, LocalDateTime threshold);

    /** Fetch orders for a restaurant filtered by status and date range (inclusive start, exclusive end). */
    @Query("SELECT o FROM OrderDAO o WHERE o.restaurantId = :restaurantId " +
           "AND o.status = :status " +
           "AND (cast(:start as java.time.LocalDateTime) IS NULL OR o.createdAt >= :start) " +
           "AND (cast(:end as java.time.LocalDateTime) IS NULL OR o.createdAt < :end)")
    Page<OrderDAO> findByRestaurantIdAndStatusAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("status") OrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    /** Fetch orders for a restaurant excluding a status, filtered by date range (inclusive start, exclusive end). */
    @Query("SELECT o FROM OrderDAO o WHERE o.restaurantId = :restaurantId " +
           "AND o.status <> :excludeStatus " +
           "AND (cast(:start as java.time.LocalDateTime) IS NULL OR o.createdAt >= :start) " +
           "AND (cast(:end as java.time.LocalDateTime) IS NULL OR o.createdAt < :end)")
    Page<OrderDAO> findByRestaurantIdAndStatusNotAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("excludeStatus") OrderStatus excludeStatus,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    /** Check if other active orders exist for a given table/entity in a restaurant. */
    boolean existsByRestaurantIdAndEntityNoAndStatusInAndOrderIdNot(
            Long restaurantId, String entityNo, List<OrderStatus> statuses, Long orderId);

    /** Fetch the latest active order for a given entity in a restaurant. */
    Optional<OrderDAO> findFirstByRestaurantIdAndEntityNoAndStatusInOrderByCreatedAtDesc(
            Long restaurantId, String entityNo, List<OrderStatus> statuses);

    /** Aggregate overall revenue summary for a restaurant within a date range and status list. */
    @Query("SELECT " +
           "COALESCE(SUM(o.totalAmount), 0) AS totalRevenue, " +
           "COALESCE(SUM(o.subTotal), 0) AS netSubTotal, " +
           "COALESCE(SUM(o.taxAmount), 0) AS totalTax, " +
           "COALESCE(SUM(o.serviceChargeAmount), 0) AS totalServiceCharge, " +
           "COALESCE(SUM(o.discountAmount + o.orderDiscountAmount), 0) AS totalDiscount, " +
           "COUNT(o.orderId) AS totalOrders, " +
           "COALESCE(AVG(o.prepMinutes), 0.0) AS averagePrepTime " +
           "FROM OrderDAO o " +
           "WHERE o.restaurantId = :restaurantId " +
           "AND o.status IN :statuses " +
           "AND o.createdAt >= :start " +
           "AND o.createdAt < :end")
    RevenueSummaryProjection getRevenueSummary(
            @Param("restaurantId") Long restaurantId,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Aggregate revenue by payment mode for a restaurant within a date range and status list. */
    @Query("SELECT " +
           "o.paymentMode AS paymentMode, " +
           "COALESCE(SUM(o.totalAmount), 0) AS amount, " +
           "COUNT(o.orderId) AS orderCount " +
           "FROM OrderDAO o " +
           "WHERE o.restaurantId = :restaurantId " +
           "AND o.status IN :statuses " +
           "AND o.createdAt >= :start " +
           "AND o.createdAt < :end " +
           "GROUP BY o.paymentMode")
    List<PaymentModeRevenueProjection> getRevenueByPaymentMode(
            @Param("restaurantId") Long restaurantId,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Aggregate revenue by sub-payment mode for a restaurant within a date range and status list. */
    @Query("SELECT " +
           "COALESCE(cast(o.subPaymentMode as string), 'UNSPECIFIED') AS subPaymentMode, " +
           "COALESCE(SUM(o.totalAmount), 0) AS amount, " +
           "COUNT(o.orderId) AS orderCount " +
           "FROM OrderDAO o " +
           "WHERE o.restaurantId = :restaurantId " +
           "AND o.status IN :statuses " +
           "AND o.createdAt >= :start " +
           "AND o.createdAt < :end " +
           "GROUP BY o.subPaymentMode")
    List<SubPaymentModeRevenueProjection> getRevenueBySubPaymentMode(
            @Param("restaurantId") Long restaurantId,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Aggregate revenue by order type for a restaurant within a date range and status list. */
    @Query("SELECT " +
           "o.orderType AS orderType, " +
           "COALESCE(SUM(o.totalAmount), 0) AS amount, " +
           "COUNT(o.orderId) AS orderCount " +
           "FROM OrderDAO o " +
           "WHERE o.restaurantId = :restaurantId " +
           "AND o.status IN :statuses " +
           "AND o.createdAt >= :start " +
           "AND o.createdAt < :end " +
           "GROUP BY o.orderType")
    List<OrderTypeRevenueProjection> getRevenueByOrderType(
            @Param("restaurantId") Long restaurantId,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Aggregate order counts by status within a date range. */
    @Query("SELECT " +
           "o.status AS status, " +
           "COUNT(o.orderId) AS orderCount " +
           "FROM OrderDAO o " +
           "WHERE o.restaurantId = :restaurantId " +
           "AND o.createdAt >= :start " +
           "AND o.createdAt < :end " +
           "GROUP BY o.status")
    List<OrderStatusCountProjection> getOrderStatusCounts(
            @Param("restaurantId") Long restaurantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}


