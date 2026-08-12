package com.kitchen.order.dto.response.dashboard;

import com.kitchen.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusCountDTO {
    private OrderStatus status;
    private Long orderCount;
}
