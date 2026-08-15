package com.kitchen.order.dto.request;

import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.enums.SubPaymentMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrderStatusRequest {

    @NotNull(message = "status is required")
    private OrderStatus status;

    /**
     * Optional reason for the status transition.
     */
    private String reason;

    /**
     * Optional preparation time (minutes) or extra delay time.
     */
    private Integer prepMinutes;

    /**
     * Optional sub-payment mode (e.g. CARD, UPI, CASH) for updating payment detail on status change.
     */
    private SubPaymentMode subPaymentMode;
}

