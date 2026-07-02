package com.restaurant.service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantCharge implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String name;        // e.g. "CGST", "SGST", "Service Charge"
    private String type;        // "PERCENTAGE" or "FIXED"
    private BigDecimal value;   // e.g. 2.5, 5.0
    private String category;    // "TAX", "SERVICE_CHARGE", or "DISCOUNT"
}
