package com.kitchen.order.service;

import com.razorpay.RazorpayException;
import java.math.BigDecimal;

public interface IPaymentService {

    String createOrder(Long orderId, BigDecimal totalAmount, String keyId, String keySecret) throws RazorpayException;

    boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature, String keySecret);
}
