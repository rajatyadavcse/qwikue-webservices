package com.kitchen.order.scheduler;

import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.enums.PaymentStatus;
import com.kitchen.order.repository.OrderRepository;
import com.kitchen.order.service.IOrderService;
import com.kitchen.order.service.RestaurantValidationService;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically sweeps and resolves stale/unresolved payments on Razorpay.
 */
@Component
@ConditionalOnProperty(name = "app.payment-sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentSweeperScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentSweeperScheduler.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private IOrderService orderService;

    @Autowired
    private RestaurantValidationService validationService;

    @Value("${app.payment-sweeper.stale-minutes:15}")
    private int staleMinutes;

    @Scheduled(cron = "${app.payment-sweeper.cron:0 */10 * * * *}")
    public void sweepStalePayments() {
        log.info("Running Payment Sweeper job to check for stale payments...");

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(staleMinutes);
        List<OrderDAO> staleOrders = orderRepository.findByPaymentModeAndPaymentStatusAndCreatedAtBefore(
                PaymentMode.ONLINE, PaymentStatus.PENDING, threshold);

        if (staleOrders.isEmpty()) {
            log.info("No stale ONLINE PENDING payments found.");
            return;
        }

        log.info("Found {} stale ONLINE PENDING orders for processing", staleOrders.size());

        for (OrderDAO order : staleOrders) {
            try {
                processStaleOrder(order);
            } catch (Exception e) {
                log.error("Failed to process stale payment verification for orderId={}: {}", 
                        order.getOrderId(), e.getMessage(), e);
            }
        }
    }

    private void processStaleOrder(OrderDAO order) {
        log.info("Processing stale payment check for orderId={}, razorpayOrderId={}", 
                order.getOrderId(), order.getRazorpayOrderId());

        if (order.getRazorpayOrderId() == null || order.getRazorpayOrderId().isBlank()) {
            log.warn("Stale order {} has no razorpayOrderId. Failing payment directly.", order.getOrderId());
            orderService.failPayment(order.getOrderId(), "No Razorpay Order ID created");
            return;
        }

        // 1. Fetch restaurant credentials
        RestaurantValidationService.RestaurantResponse restaurant;
        try {
            restaurant = validationService.validateRestaurant(order.getRestaurantId());
        } catch (Exception e) {
            log.error("Could not fetch restaurant details for restaurantId={} (orderId={}): {}", 
                    order.getRestaurantId(), order.getOrderId(), e.getMessage());
            return; // skip to retry on next run
        }

        String keyId = restaurant.getRazorpayKeyId();
        String keySecret = restaurant.getRazorpayKeySecret();

        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            log.error("Razorpay credentials missing for restaurantId={} (orderId={}). Skipping.", 
                    order.getRestaurantId(), order.getOrderId());
            return; // skip to retry on next run
        }

        // 2. Query Razorpay API
        try {
            RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
            com.razorpay.Order razorpayOrder = razorpay.orders.fetch(order.getRazorpayOrderId());
            String razorpayStatus = razorpayOrder.get("status");

            log.info("Order ID {} -> Razorpay status: {}", order.getOrderId(), razorpayStatus);

            if ("paid".equalsIgnoreCase(razorpayStatus)) {
                // Safeguard Auto-complete
                List<com.razorpay.Payment> payments = razorpay.orders.fetchPayments(order.getRazorpayOrderId());
                String paymentId = null;
                if (payments != null) {
                    for (com.razorpay.Payment payment : payments) {
                        String pStatus = payment.get("status");
                        if ("captured".equalsIgnoreCase(pStatus) || "authorized".equalsIgnoreCase(pStatus)) {
                            paymentId = payment.get("id");
                            break;
                        }
                    }
                }
                if (paymentId == null) {
                    if (payments != null && !payments.isEmpty()) {
                        paymentId = payments.get(0).get("id");
                    } else {
                        paymentId = "SYSTEM_AUTO_CAPTURED_" + order.getRazorpayOrderId();
                    }
                }
                log.info("Auto-completing paid orderId={} with paymentId={}", order.getOrderId(), paymentId);
                orderService.completePayment(order.getOrderId(), paymentId);
            } else {
                // Not paid, window expired -> Fail payment and cancel order
                log.info("Failing unpaid stale orderId={} (Razorpay status={})", order.getOrderId(), razorpayStatus);
                orderService.failPayment(order.getOrderId(), 
                        "Payment verification window expired. Razorpay status: " + razorpayStatus);
            }

        } catch (RazorpayException e) {
            boolean isOrderNotFound = false;
            try {
                JSONObject errorResponse = new JSONObject(e.getMessage());
                if (errorResponse.has("error")) {
                    JSONObject errorDetails = errorResponse.getJSONObject("error");
                    String code = errorDetails.optString("code", "");
                    String description = errorDetails.optString("description", "");
                    if ("BAD_REQUEST_ERROR".equalsIgnoreCase(code) || description.toLowerCase().contains("not found")) {
                        isOrderNotFound = true;
                    }
                }
            } catch (Exception parseEx) {
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (msg.contains("bad_request_error") || msg.contains("not found") || msg.contains("does not exist")) {
                    isOrderNotFound = true;
                }
            }

            if (isOrderNotFound) {
                log.warn("Razorpay reported order not found or invalid for orderId={}. Failing payment. Error: {}", 
                        order.getOrderId(), e.getMessage());
                orderService.failPayment(order.getOrderId(), "Razorpay order invalid: " + e.getMessage());
            } else {
                log.error("Error communicating with Razorpay for orderId={} (will retry): {}", 
                        order.getOrderId(), e.getMessage(), e);
            }
        }
    }
}
