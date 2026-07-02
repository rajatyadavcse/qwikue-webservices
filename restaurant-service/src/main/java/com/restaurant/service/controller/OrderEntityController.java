package com.restaurant.service.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.service.model.OrderEntity;
import com.restaurant.service.service.IOrderEntityService;

@RestController
@RequestMapping("/entities")
public class OrderEntityController {

    @Autowired
    IOrderEntityService orderEntityService;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderEntity> createOrderEntity(@RequestBody OrderEntity orderEntity) {
        return new ResponseEntity<>(orderEntityService.createOrderEntity(orderEntity), HttpStatus.CREATED);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OrderEntity>> getOrderEntitiesByRestaurantId(@RequestParam Long restaurantId) {
        return new ResponseEntity<>(orderEntityService.getOrderEntitiesByRestaurantId(restaurantId), HttpStatus.OK);
    }

    @GetMapping(value = "/{entityNo}/restaurant/{restaurantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderEntity> getOrderEntityById(@PathVariable String entityNo, @PathVariable Long restaurantId) {
        return new ResponseEntity<>(orderEntityService.getOrderEntityById(entityNo, restaurantId), HttpStatus.OK);
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderEntity> updateOrderEntity(@RequestBody OrderEntity orderEntity) {
        return new ResponseEntity<>(orderEntityService.updateOrderEntity(orderEntity), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{entityNo}/restaurant/{restaurantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteOrderEntity(@PathVariable String entityNo, @PathVariable Long restaurantId) {
        orderEntityService.deleteOrderEntity(entityNo, restaurantId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
