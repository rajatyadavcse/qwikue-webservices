package com.restaurant.service.service;

import com.restaurant.service.event.OrderEntityUpdateEvent;
import com.restaurant.service.model.OrderEntity;
import com.restaurant.service.model.OrderEntityStatus;
import com.restaurant.service.model.OrderEntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OrderEntityStreamServiceTest {

    private OrderEntityStreamService streamService;

    @BeforeEach
    void setUp() {
        streamService = new OrderEntityStreamService();
    }

    @Test
    void testSubscribeToRestaurant_returnsEmitter() {
        SseEmitter emitter = streamService.subscribeToRestaurant(1L);
        assertNotNull(emitter);
    }

    @Test
    void testHandleOrderEntityUpdateEvent_broadcastsToSubscribers() {
        SseEmitter emitter = streamService.subscribeToRestaurant(1L);
        assertNotNull(emitter);

        OrderEntity entity = new OrderEntity();
        entity.setEntityNo("Table-1");
        entity.setRestaurantId(1L);
        entity.setStatus(OrderEntityStatus.OCCUPIED.name());
        entity.setOrderEntityType(OrderEntityType.TABLE);

        OrderEntityUpdateEvent event = new OrderEntityUpdateEvent(this, entity);

        assertDoesNotThrow(() -> streamService.handleOrderEntityUpdateEvent(event));
    }

    @Test
    void testHandleOrderEntityUpdateEvent_nullOrIncompleteEvent_handledGracefully() {
        assertDoesNotThrow(() -> streamService.handleOrderEntityUpdateEvent(null));

        OrderEntityUpdateEvent eventWithNullEntity = new OrderEntityUpdateEvent(this, null);
        assertDoesNotThrow(() -> streamService.handleOrderEntityUpdateEvent(eventWithNullEntity));

        OrderEntity entityWithoutRestaurantId = new OrderEntity();
        entityWithoutRestaurantId.setEntityNo("Table-1");
        OrderEntityUpdateEvent eventWithoutRestId = new OrderEntityUpdateEvent(this, entityWithoutRestaurantId);
        assertDoesNotThrow(() -> streamService.handleOrderEntityUpdateEvent(eventWithoutRestId));
    }

    @Test
    void testHandleOrderEntityUpdateEvent_noActiveSubscribers_handledGracefully() {
        OrderEntity entity = new OrderEntity();
        entity.setEntityNo("Table-5");
        entity.setRestaurantId(99L);
        entity.setStatus(OrderEntityStatus.AVAILABLE.name());

        OrderEntityUpdateEvent event = new OrderEntityUpdateEvent(this, entity);

        assertDoesNotThrow(() -> streamService.handleOrderEntityUpdateEvent(event));
    }
}
