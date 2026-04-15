package com.restaurant.service.dao;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "menu")
@Data
public class MenuDAO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long menuId;

    private Long restaurantId;

    private String itemName;

    private String description;

    private BigDecimal price;

    private String category;

    private Boolean isAvailable;

    private Boolean isVeg;

    private String imageUrl;

    private Date createdDate;

    private Date updatedDate;
}
