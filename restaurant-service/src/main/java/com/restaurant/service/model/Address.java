package com.restaurant.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@Embeddable
@JsonIgnoreProperties(ignoreUnknown = true)
public class Address implements Serializable {
    private static final long serialVersionUID = 42L;

    private String addressLine1;
    private String addressLine2;
    private String landmark;
    private String city;
    private String state;
    private String country;
    private String pincode;
}
