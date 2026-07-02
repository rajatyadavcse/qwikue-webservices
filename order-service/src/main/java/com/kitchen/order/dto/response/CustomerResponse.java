package com.kitchen.order.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CustomerResponse {
    private Long customerId;
    private String customerName;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
