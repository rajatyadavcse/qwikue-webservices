package com.restaurant.service.dao;

import com.restaurant.service.model.Address;
import com.restaurant.service.model.EstablishmentType;
import com.restaurant.service.model.OrderEntityType;
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

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Embedded
    private Address address;

    private String phoneNo;

    private Date createdDate;

    private Date updatedDate;

    private String status;

    private String email;

    private String type;

    @Enumerated(EnumType.STRING)
    private EstablishmentType establishmentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "order_entity_types", columnDefinition = "jsonb")
    private List<OrderEntityType> orderEntityTypes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "taxes_and_charges", columnDefinition = "jsonb")
    private List<RestaurantCharge> taxesAndCharges = new ArrayList<>();

    @Column(name = "razorpay_linked_account_id", length = 50)
    private String razorpayLinkedAccountId;

    @Column(name = "razorpay_key_id")
    @Convert(converter = com.restaurant.service.util.AttributeCryptoConverter.class)
    private String razorpayKeyId;

    @Column(name = "razorpay_key_secret")
    @Convert(converter = com.restaurant.service.util.AttributeCryptoConverter.class)
    private String razorpayKeySecret;
}

