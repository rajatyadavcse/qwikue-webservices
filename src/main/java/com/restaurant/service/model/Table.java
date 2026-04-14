package com.restaurant.service.model;

import java.util.Date;

import lombok.Data;

@Data
public class Table {

    private Long tableNo;

    private Long restaurantId;

    private String status;

    private String QRUrl;

    private Date createdDate;

    private Date updatedDate;

}
