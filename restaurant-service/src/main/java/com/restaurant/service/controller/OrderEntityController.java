package com.restaurant.service.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.swagger.v3.oas.annotations.Operation;

import com.restaurant.service.model.OrderEntity;
import com.restaurant.service.service.IOrderEntityService;
import com.restaurant.service.service.OrderEntityStreamService;

@RestController
@RequestMapping("/entities")
public class OrderEntityController {

    @Autowired
    IOrderEntityService orderEntityService;

    @Autowired
    OrderEntityStreamService orderEntityStreamService;

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

    @Operation(summary = "Stream all restaurant entity updates for dashboards", description = "Standard HTTP-based SSE stream.")
    @GetMapping(value = "/restaurant/{restaurantId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRestaurantEntityUpdates(@PathVariable Long restaurantId) {
        return orderEntityStreamService.subscribeToRestaurant(restaurantId);
    }

    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderEntity> updateOrderEntity(@RequestBody OrderEntity orderEntity) {
        return new ResponseEntity<>(orderEntityService.updateOrderEntity(orderEntity), HttpStatus.OK);
    }

    @PatchMapping(value = "/{entityNo}/restaurant/{restaurantId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderEntity> updateOrderEntityStatus(
            @PathVariable String entityNo,
            @PathVariable Long restaurantId,
            @RequestParam com.restaurant.service.model.OrderEntityStatus status) {
        return new ResponseEntity<>(orderEntityService.updateOrderEntityStatus(entityNo, restaurantId, status), HttpStatus.OK);
    }

    @DeleteMapping(value = "/{entityNo}/restaurant/{restaurantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteOrderEntity(@PathVariable String entityNo, @PathVariable Long restaurantId) {
        orderEntityService.deleteOrderEntity(entityNo, restaurantId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
