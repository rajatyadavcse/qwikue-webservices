package com.kitchen.order.repository;

import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.EntityGraph;
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
import com.kitchen.order.repository.projection.TipPaymentModeRevenueProjection;

@Repository
public interface OrderRepository extends JpaRepository<OrderDAO, Long> {

    /** Fetch all orders for a restaurant, paginated. */
    @EntityGraph(attributePaths = {"customer"})
    Page<OrderDAO> findByRestaurantId(Long restaurantId, Pageable pageable);

    /** Fetch all orders for a restaurant excluding a specific status, paginated. */
    @EntityGraph(attributePaths = {"customer"})
    Page<OrderDAO> findByRestaurantIdAndStatusNot(Long restaurantId, OrderStatus status, Pageable pageable);

    /** Fetch orders for a restaurant filtered by status, paginated. */
    @EntityGraph(attributePaths = {"customer"})
    Page<OrderDAO> findByRestaurantIdAndStatus(Long restaurantId, OrderStatus status, Pageable pageable);

    /** Fetch active kitchen orders (non-terminal statuses) for a specific restaurant. */
    @EntityGraph(attributePaths = {"customer", "items"})
    List<OrderDAO> findByRestaurantIdAndStatusIn(Long restaurantId, List<OrderStatus> statuses);

    /** Fetch online orders that are pending payment and created before the threshold time. */
    @EntityGraph(attributePaths = {"customer", "items"})
    List<OrderDAO> findByPaymentModeAndPaymentStatusAndCreatedAtBefore(
            PaymentMode paymentMode, PaymentStatus paymentStatus, LocalDateTime threshold);

    /** Fetch orders for a restaurant filtered by status and date range (inclusive start, exclusive end). */
    @EntityGraph(attributePaths = {"customer"})
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
    @EntityGraph(attributePaths = {"customer"})
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

    /** Optimized queries without dynamic null checks for precise index usage */
    @EntityGraph(attributePaths = {"customer"})
    Page<OrderDAO> findByRestaurantIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long restaurantId, OrderStatus status, LocalDateTime start, LocalDateTime end, Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    Page<OrderDAO> findByRestaurantIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long restaurantId, OrderStatus excludeStatus, LocalDateTime start, LocalDateTime end, Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    Page<OrderDAO> findByRestaurantIdAndStatusAndCreatedAtGreaterThanEqual(
            Long restaurantId, OrderStatus status, LocalDateTime start, Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    Page<OrderDAO> findByRestaurantIdAndStatusNotAndCreatedAtGreaterThanEqual(
            Long restaurantId, OrderStatus excludeStatus, LocalDateTime start, Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    Page<OrderDAO> findByRestaurantIdAndStatusAndCreatedAtLessThan(
            Long restaurantId, OrderStatus status, LocalDateTime end, Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    Page<OrderDAO> findByRestaurantIdAndStatusNotAndCreatedAtLessThan(
            Long restaurantId, OrderStatus excludeStatus, LocalDateTime end, Pageable pageable);

    /** Check if active orders exist for a given table/entity in a restaurant. */
    boolean existsByRestaurantIdAndEntityNoAndStatusIn(
            Long restaurantId, String entityNo, List<OrderStatus> statuses);

    /** Check if other active orders exist for a given table/entity in a restaurant. */
    boolean existsByRestaurantIdAndEntityNoAndStatusInAndOrderIdNot(
            Long restaurantId, String entityNo, List<OrderStatus> statuses, Long orderId);

    /** Fetch the latest active order for a given entity in a restaurant. */
    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<OrderDAO> findFirstByRestaurantIdAndEntityNoAndStatusInOrderByCreatedAtDesc(
            Long restaurantId, String entityNo, List<OrderStatus> statuses);

    /** Aggregate overall revenue summary for a restaurant within a date range and status list. */
    @Query("SELECT " +
           "COALESCE(SUM(o.totalAmount), 0) AS totalRevenue, " +
           "COALESCE(SUM(o.tipAmount), 0) AS totalTip, " +
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
    @Query(value = "SELECT combined.payment_mode_cat AS paymentMode, COALESCE(SUM(combined.amount), 0) AS amount, COUNT(DISTINCT combined.order_id) AS orderCount " +
           "FROM (" +
           "  SELECT o.order_id, o.payment_mode AS payment_mode_cat, o.total_amount AS amount " +
           "  FROM \"order\".orders o " +
           "  WHERE o.restaurant_id = :restaurantId " +
           "  AND o.status IN (:statuses) " +
           "  AND o.created_at >= :start " +
           "  AND o.created_at < :end " +
           "  AND (o.sub_payment_mode IS NULL OR o.sub_payment_mode != 'SPLIT') " +
           "  UNION ALL " +
           "  SELECT o.order_id, CASE WHEN UPPER(elem->>'mode') = 'CASH' THEN 'CASH' ELSE 'ONLINE' END AS payment_mode_cat, CAST(elem->>'amount' AS numeric) AS amount " +
           "  FROM \"order\".orders o, jsonb_array_elements(o.split_payments) AS elem " +
           "  WHERE o.restaurant_id = :restaurantId " +
           "  AND o.status IN (:statuses) " +
           "  AND o.created_at >= :start " +
           "  AND o.created_at < :end " +
           "  AND o.sub_payment_mode = 'SPLIT' " +
           ") combined " +
           "GROUP BY combined.payment_mode_cat", nativeQuery = true)
    List<PaymentModeRevenueProjection> getRevenueByPaymentMode(
            @Param("restaurantId") Long restaurantId,
            @Param("statuses") List<String> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Aggregate revenue by sub-payment mode for a restaurant within a date range and status list. */
    @Query(value = "SELECT combined.sub_mode AS subPaymentMode, COALESCE(SUM(combined.amount), 0) AS amount, COUNT(DISTINCT combined.order_id) AS orderCount " +
           "FROM (" +
           "  SELECT o.order_id, COALESCE(o.sub_payment_mode, 'UNSPECIFIED') AS sub_mode, o.total_amount AS amount " +
           "  FROM \"order\".orders o " +
           "  WHERE o.restaurant_id = :restaurantId " +
           "  AND o.status IN (:statuses) " +
           "  AND o.created_at >= :start " +
           "  AND o.created_at < :end " +
           "  AND (o.sub_payment_mode IS NULL OR o.sub_payment_mode != 'SPLIT') " +
           "  UNION ALL " +
           "  SELECT o.order_id, elem->>'mode' AS sub_mode, CAST(elem->>'amount' AS numeric) AS amount " +
           "  FROM \"order\".orders o, jsonb_array_elements(o.split_payments) AS elem " +
           "  WHERE o.restaurant_id = :restaurantId " +
           "  AND o.status IN (:statuses) " +
           "  AND o.created_at >= :start " +
           "  AND o.created_at < :end " +
           "  AND o.sub_payment_mode = 'SPLIT' " +
           ") combined " +
           "GROUP BY combined.sub_mode", nativeQuery = true)
    List<SubPaymentModeRevenueProjection> getRevenueBySubPaymentMode(
            @Param("restaurantId") Long restaurantId,
            @Param("statuses") List<String> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** Aggregate tip revenue by tip payment mode for a restaurant within a date range and status list. */
    @Query(value = "SELECT COALESCE(o.tip_payment_mode, 'UNSPECIFIED') AS tipPaymentMode, " +
           "COALESCE(SUM(o.tip_amount), 0) AS amount, " +
           "COUNT(o.order_id) AS orderCount " +
           "FROM \"order\".orders o " +
           "WHERE o.restaurant_id = :restaurantId " +
           "AND o.status IN (:statuses) " +
           "AND o.created_at >= :start " +
           "AND o.created_at < :end " +
           "AND o.tip_amount > 0 " +
           "GROUP BY COALESCE(o.tip_payment_mode, 'UNSPECIFIED')", nativeQuery = true)
    List<TipPaymentModeRevenueProjection> getRevenueByTipPaymentMode(
            @Param("restaurantId") Long restaurantId,
            @Param("statuses") List<String> statuses,
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


