package com.restaurant.service.dao;

import java.util.Date;

import com.restaurant.service.model.OrderEntityType;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "order_entity", schema = "restaurant")
@Data
public class OrderEntityDAO {

    @EmbeddedId
    private OrderEntityId orderEntityId;

    @Enumerated(EnumType.STRING)
    private OrderEntityType orderEntityType;

    private String status;

    private String QRUrl;

    private Date createdDate;

    private Date updatedDate;

}
