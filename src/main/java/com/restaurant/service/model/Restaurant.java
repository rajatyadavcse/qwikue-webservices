package com.restaurant.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Restaurant implements Serializable {
    @Serial
    private static final long serialVersionUID = 42L;

    private Long restaurantId;

    private String restaurantName;

    private String addressName;

    private String phoneNo;

    private Date createdDate;

    private Date updatedDate;

    private String status;

    @Email
    private String email;

    private String type;

    private EstablishmentType establishmentType;

    private List<OrderEntityType> orderEntityTypes;

    private List<RestaurantCharge> taxesAndCharges;
}

