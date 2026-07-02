package com.restaurant.service.dao;

import java.io.Serializable;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntityId implements Serializable {

    private String entityNo;

    private Long restaurantId;

}
