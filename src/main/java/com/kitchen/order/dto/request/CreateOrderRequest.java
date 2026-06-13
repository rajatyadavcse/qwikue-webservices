package com.kitchen.order.dto.request;

import com.kitchen.order.enums.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotNull(message = "restaurantId is required")
    private Long restaurantId;

    @NotBlank(message = "entityNo is required")
    private String entityNo;

    /** Optional customer notes (e.g. "no onions, extra spice") */
    private String notes;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    private PaymentMode paymentMode = PaymentMode.CASH;
}
