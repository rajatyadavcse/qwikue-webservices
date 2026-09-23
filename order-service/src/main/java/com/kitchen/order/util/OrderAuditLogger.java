package com.kitchen.order.util;

import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.enums.OrderType;
import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.enums.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.math.BigDecimal;

public class OrderAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(OrderAuditLogger.class);

    public static void logStatusTransition(Long orderId, Long restaurantId, OrderStatus oldStatus,
                                          OrderStatus newStatus, PaymentStatus paymentStatus,
                                          PaymentMode paymentMode, BigDecimal totalAmount,
                                          String flowSource, String reason) {
        String httpMethod = MDC.get("httpMethod");
        String requestUri = MDC.get("requestUri");
        String apiInfo = (httpMethod != null && requestUri != null) ? " (" + httpMethod + " " + requestUri + ")" : "";

        log.info("[ORDER_AUDIT] Flow Source: {}{} | Order {} status updated: {} → {} for restaurantId {} | PaymentStatus: {}, PaymentMode: {}, Total: {} | Reason: {}",
                flowSource, apiInfo, orderId, oldStatus, newStatus, restaurantId, paymentStatus, paymentMode, totalAmount,
                (reason != null ? reason : "N/A"));
    }

    public static void logEntityChange(Long orderId, Long restaurantId, String oldEntityNo,
                                      String oldEntityType, String newEntityNo,
                                      String newEntityType, OrderType oldOrderType,
                                      OrderType newOrderType, String flowSource) {
        String httpMethod = MDC.get("httpMethod");
        String requestUri = MDC.get("requestUri");
        String apiInfo = (httpMethod != null && requestUri != null) ? " (" + httpMethod + " " + requestUri + ")" : "";

        log.info("[ORDER_AUDIT] Flow Source: {}{} | Order {} entity updated for restaurantId {} | entityNo: '{}' ({}) → '{}' ({}) | orderType: {} → {}",
                flowSource, apiInfo, orderId, restaurantId,
                (oldEntityNo != null ? oldEntityNo : "NONE"), (oldEntityType != null ? oldEntityType : "NONE"),
                (newEntityNo != null ? newEntityNo : "NONE"), (newEntityType != null ? newEntityType : "NONE"),
                oldOrderType, newOrderType);
    }

    public static void logEntityRelease(Long orderId, Long restaurantId, String entityNo,
                                       OrderStatus orderStatus, String flowSource) {
        String httpMethod = MDC.get("httpMethod");
        String requestUri = MDC.get("requestUri");
        String apiInfo = (httpMethod != null && requestUri != null) ? " (" + httpMethod + " " + requestUri + ")" : "";

        log.info("[ORDER_AUDIT] Flow Source: {}{} | Entity release check for entityNo: '{}' (restaurantId {}) triggered by order {} in status {}",
                flowSource, apiInfo, entityNo, restaurantId, orderId, orderStatus);
    }

    public static void logRapidUpdateWarning(Long orderId, long msSinceLastUpdate, String threadName,
                                            String flowSource, String idempotencyKey) {
        String httpMethod = MDC.get("httpMethod");
        String requestUri = MDC.get("requestUri");
        String apiInfo = (httpMethod != null && requestUri != null) ? " (" + httpMethod + " " + requestUri + ")" : "";

        log.warn("[RAPID_UPDATE_WARN] Order {} received rapid consecutive update request within {} ms! Flow Source: {}{} | Thread: [{}] | IdempotencyKey: {}",
                orderId, msSinceLastUpdate, flowSource, apiInfo, threadName,
                (idempotencyKey != null ? idempotencyKey : "MISSING"));
    }

    public static void logTableTransfer(Long orderId, String oldEntityNo, String newEntityNo, String updatedBy) {
        String httpMethod = MDC.get("httpMethod");
        String requestUri = MDC.get("requestUri");
        String apiInfo = (httpMethod != null && requestUri != null) ? " (" + httpMethod + " " + requestUri + ")" : "";

        log.warn("[ORDER_AUDIT_WARN] Table Transfer | Order {} entity update: '{}' → '{}' | Updated By: {} | ApiInfo: {}",
                orderId,
                (oldEntityNo != null ? oldEntityNo : "NONE"),
                (newEntityNo != null ? newEntityNo : "NONE"),
                (updatedBy != null ? updatedBy : "UNKNOWN"),
                apiInfo);
    }
}

