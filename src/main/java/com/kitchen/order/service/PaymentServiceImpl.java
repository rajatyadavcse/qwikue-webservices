package com.kitchen.order.service;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PaymentServiceImpl implements IPaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Override
    public String createOrder(Long orderId, BigDecimal totalAmount, String keyId, String keySecret) throws RazorpayException {
        log.info("Creating Razorpay Order for orderId={}, totalAmount={}", orderId, totalAmount);

        // Convert total amount to Paise (Razorpay expects subunits)
        int totalAmountInPaise = totalAmount.multiply(new BigDecimal(100)).intValue();

        // Initialize RazorpayClient using restaurant-specific credentials
        RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);

        // Prepare Order Request payload
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", totalAmountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "ORDER_REC_" + orderId);

        // Fire order creation API
        com.razorpay.Order order = razorpay.orders.create(orderRequest);
        return order.get("id");
    }

    @Override
    public boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature, String keySecret) {
        log.info("Verifying Razorpay Signature for razorpayOrderId={}, razorpayPaymentId={}", 
                razorpayOrderId, razorpayPaymentId);
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", razorpayOrderId);
            options.put("razorpay_payment_id", razorpayPaymentId);
            options.put("razorpay_signature", razorpaySignature);

            return Utils.verifyPaymentSignature(options, keySecret);
        } catch (Exception e) {
            log.error("Signature verification failed with exception: {}", e.getMessage());
            return false;
        }
    }
}
