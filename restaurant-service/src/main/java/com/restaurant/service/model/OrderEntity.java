package com.restaurant.service.model;

import java.util.Date;
import lombok.Data;

@Data
public class OrderEntity {

    private String entityNo;

    private Long restaurantId;

    private OrderEntityType orderEntityType;

    private String status;

    private String QRUrl;

    private Date createdDate;

    private Date updatedDate;

}
