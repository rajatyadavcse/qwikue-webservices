package com.restaurant.service.dao;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class TableId implements Serializable {
    private Long tableNo;

    private Long restaurantId;
}
