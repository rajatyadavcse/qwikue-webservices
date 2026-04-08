package com.restaurant.service.dao;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

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

}
