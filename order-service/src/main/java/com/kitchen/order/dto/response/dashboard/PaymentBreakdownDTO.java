package com.kitchen.order.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentBreakdownDTO {
    @Builder.Default
    private List<PaymentModeBreakdownDTO> byPaymentMode = new ArrayList<>();

    @Builder.Default
    private List<SubPaymentModeBreakdownDTO> bySubPaymentMode = new ArrayList<>();
}
