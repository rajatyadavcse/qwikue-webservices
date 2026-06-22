package com.kitchen.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderAppliedCharge implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String name;               // e.g. "CGST", "SGST", "Service Charge"
    private String type;               // "PERCENTAGE" or "FIXED"
    private BigDecimal appliedRate;    // e.g. 2.50
    private BigDecimal calculatedAmount; // calculated dollar amount
    private String category;             // "TAX", "SERVICE_CHARGE", or "DISCOUNT"
}
