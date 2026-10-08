package com.kitchen.order.dto.response.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyItemSalesDTO {
    private LocalDate date;
    private Long quantitySold;
    private BigDecimal revenue;
    private Long orderCount;
}
