package com.restaurant.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Menu implements Serializable {
    @Serial
    private static final long serialVersionUID = 43L;

    private Long menuId;

    private Long restaurantId;

    private String itemName;

    private String description;

    private BigDecimal price;

    private String category;

    private Boolean isVeg;

    private Boolean isAvailable;

    private String imageUrl;

    private Date createdDate;

    private Date updatedDate;
}
