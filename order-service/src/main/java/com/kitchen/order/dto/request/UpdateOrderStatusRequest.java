package com.kitchen.order.dto.request;

import com.kitchen.order.enums.OrderStatus;
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
}
