package com.kitchen.order.service;

import com.razorpay.RazorpayException;
import java.math.BigDecimal;

public interface IPaymentService {

    String createRoutedOrder(Long orderId, BigDecimal totalAmount, String linkedAccountId) throws RazorpayException;

    boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature);
}
