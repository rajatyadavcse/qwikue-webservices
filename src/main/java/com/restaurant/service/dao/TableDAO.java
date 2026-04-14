package com.restaurant.service.dao;

import java.util.Date;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "dinning_table")
@Data
public class TableDAO {

    @EmbeddedId
    private TableId tableId;

    private String status;

    private String QRUrl;

    private Date createdDate;

    private Date updatedDate;

}
