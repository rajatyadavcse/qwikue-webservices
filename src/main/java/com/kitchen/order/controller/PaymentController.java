package com.kitchen.order.controller;

import com.kitchen.order.dto.request.PaymentVerificationRequest;
import com.kitchen.order.dto.response.OrderResponse;
import com.kitchen.order.service.IPaymentService;
import com.kitchen.order.service.IOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Razorpay payment integration endpoints")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private IPaymentService paymentService;

    @Autowired
    private IOrderService orderService;

    @Autowired
    private com.kitchen.order.service.RestaurantValidationService validationService;

    @Operation(
            summary = "Verify Razorpay payment signature",
            description = "Verifies the cryptographic payment signature returned by Razorpay Checkout. " +
                          "If valid, marks payment status as COMPLETED and order status as PENDING."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment verified and order status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment signature or verification failed"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PostMapping(value = "/verify", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> verifyPayment(@Valid @RequestBody PaymentVerificationRequest request) {
        log.info("Received payment verification request for orderId={}", request.getOrderId());
        
        OrderResponse order = orderService.getOrderById(request.getOrderId());
        if (order == null) {
            log.warn("Order not found for orderId={}", request.getOrderId());
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        // Fetch restaurant details using the restaurantId from the order to get the keySecret
        com.kitchen.order.service.RestaurantValidationService.RestaurantResponse restaurant = 
                validationService.validateRestaurant(order.getRestaurantId());
        
        boolean isValidSignature = paymentService.verifyPaymentSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature(),
                restaurant.getRazorpayKeySecret()
        );

        if (!isValidSignature) {
            log.warn("Payment signature verification failed for orderId={}", request.getOrderId());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        OrderResponse updatedOrder = orderService.completePayment(request.getOrderId(), request.getRazorpayPaymentId());
        return ResponseEntity.ok(updatedOrder);
    }

}
