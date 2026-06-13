package com.kitchen.order.service;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PaymentServiceImpl implements IPaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Value("${razorpay.platform-commission-percentage}")
    private double commissionPercentage;

    @Override
    public String createRoutedOrder(Long orderId, BigDecimal totalAmount, String linkedAccountId) throws RazorpayException {
        log.info("Creating Razorpay Route Order for orderId={}, totalAmount={}, linkedAccountId={}", 
                orderId, totalAmount, linkedAccountId);

        // Convert total amount to Paise (Razorpay expects subunits)
        int totalAmountInPaise = totalAmount.multiply(new BigDecimal(100)).intValue();

        // Calculate commissions & transfer amount
        double platformFraction = commissionPercentage / 100.0;
        BigDecimal commissionAmount = totalAmount.multiply(BigDecimal.valueOf(platformFraction)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal transferAmount = totalAmount.subtract(commissionAmount).setScale(2, RoundingMode.HALF_UP);
        int transferAmountInPaise = transferAmount.multiply(new BigDecimal(100)).intValue();

        RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);

        // Prepare Order Request payload
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", totalAmountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "ORDER_REC_" + orderId);

        if (linkedAccountId != null && !linkedAccountId.isBlank()) {
            // Define the Route split transfer parameters
            JSONArray transfers = new JSONArray();
            JSONObject transfer = new JSONObject();
            transfer.put("account", linkedAccountId); // Destination sub-merchant ID
            transfer.put("amount", transferAmountInPaise); // Restaurant's share in paise
            transfer.put("currency", "INR");
            transfer.put("on_hold", false); // If true, funds can be held and released later via API
            transfers.put(transfer);

            orderRequest.put("transfers", transfers);
        } else {
            log.warn("linkedAccountId is empty/null, creating standard payment without automatic routing split.");
        }

        // Fire order creation API
        com.razorpay.Order order = razorpay.orders.create(orderRequest);
        return order.get("id");
    }

    @Override
    public boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
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
