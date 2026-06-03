package com.restaurant.service.dao;

import com.restaurant.service.model.RestaurantCharge;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "restaurant")
@Data
public class RestaurantDAO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long restaurantId;

    private String restaurantName;

    private String addressName;

    private String phoneNo;

    private Date createdDate;

    private Date updatedDate;

    private String status;

    private String email;

    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "taxes_and_charges", columnDefinition = "jsonb")
    private List<RestaurantCharge> taxesAndCharges = new ArrayList<>();
}

