package com.kitchen.order.repository;

import com.kitchen.order.dao.RestaurantTokenCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface RestaurantTokenCounterRepository extends JpaRepository<RestaurantTokenCounter, Long> {

    @Query(value = "INSERT INTO \"order\".restaurant_token_counters (restaurant_id, counter_date, last_token_no) " +
                   "VALUES (:restaurantId, :counterDate, 1) " +
                   "ON CONFLICT (restaurant_id, counter_date) " +
                   "DO UPDATE SET last_token_no = \"order\".restaurant_token_counters.last_token_no + 1 " +
                   "RETURNING last_token_no", nativeQuery = true)
    int getNextTokenNo(@Param("restaurantId") Long restaurantId, @Param("counterDate") LocalDate counterDate);
}
