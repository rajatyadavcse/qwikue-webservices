package com.kitchen.order.dto.request;

import com.kitchen.order.enums.OrderType;
import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.enums.SubPaymentMode;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class UpdateOrderRequest {

    /**
     * Optional updated entity/table number.
     */
    private String entityNo;

    /**
     * Optional updated order type (DINE_IN, TAKE_AWAY).
     */
    private OrderType orderType;

    /**
     * Optional updated customer name.
     */
    private String customerName;

    /**
     * Optional updated customer phone.
     */
    private String phone;

    /**
     * Optional customer notes (e.g. "no onions, extra spice").
     */
    private String notes;

    /**
     * Optional updated items. If provided, replaces existing items and recalculates order pricing.
     */
    @Valid
    private List<OrderItemRequest> items;

    /**
     * Optional updated payment mode.
     */
    private PaymentMode paymentMode;

    /**
     * Optional sub-payment mode (CASH, CARD, UPI) for in-restaurant CASH orders.
     */
    private SubPaymentMode subPaymentMode;

    /**
     * Optional updated order-level discount.
     */
    @Valid
    private OrderDiscountRequest discount;
}
