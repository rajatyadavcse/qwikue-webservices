package com.kitchen.order.dao;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name = "restaurant_token_counters", schema = "\"order\"", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"restaurant_id", "counter_date"})
})
@Data
public class RestaurantTokenCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "counter_date", nullable = false)
    private LocalDate counterDate;

    @Column(name = "last_token_no", nullable = false)
    private Integer lastTokenNo;
}
