package com.kitchen.order.dto.request;

import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.enums.SubPaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

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
     * Optional sub-payment mode (e.g. CARD, UPI, CASH, SPLIT) for updating payment detail on status change.
     */
    private SubPaymentMode subPaymentMode;

    /**
     * Optional tip amount added on status update.
     */
    private BigDecimal tipAmount;

    private SubPaymentMode tipPaymentMode;

    /**
     * Optional split payments list when subPaymentMode is SPLIT.
     */
    @Valid
    private List<SplitPayment> splitPayments;
}

