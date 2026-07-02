package com.kitchen.order.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentFailureRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    private String errorMessage;
}
