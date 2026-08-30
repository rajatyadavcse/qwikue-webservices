package com.kitchen.order.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitPayment {

    @NotNull(message = "mode is required for split payment item")
    private String mode;

    @NotNull(message = "amount is required for split payment item")
    private BigDecimal amount;
}
