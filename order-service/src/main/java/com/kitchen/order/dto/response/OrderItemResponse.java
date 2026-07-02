package com.kitchen.order.dto.response;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class OrderItemResponse {

    private Long orderItemId;
    private Long menuId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalItemPrice;
    private String itemName;

}
