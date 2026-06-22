package com.kitchen.order.service;

import com.kitchen.order.dto.event.OrderUpdateEvent;
import com.kitchen.order.dto.response.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class OrderStreamService {

    private static final Logger log = LoggerFactory.getLogger(OrderStreamService.class);

    // Map of orderId -> SseEmitter (Customers)
    private final Map<Long, SseEmitter> customerEmitters = new ConcurrentHashMap<>();

    // Map of restaurantId -> List of SseEmitters (Dashboards can have multiple open instances)
    private final Map<Long, List<SseEmitter>> restaurantEmitters = new ConcurrentHashMap<>();

    // Sse Emitter timeouts (in milliseconds)
    private static final long CUSTOMER_TIMEOUT = 900_000L; // 15 minutes
    private static final long RESTAURANT_TIMEOUT = 1_800_000L; // 30 minutes

    /**
     * Subscribe customer to a specific order stream.
     */
    public SseEmitter subscribeToOrder(Long orderId) {
        SseEmitter emitter = new SseEmitter(CUSTOMER_TIMEOUT);

        emitter.onCompletion(() -> customerEmitters.remove(orderId));
        emitter.onTimeout(() -> customerEmitters.remove(orderId));
        emitter.onError((e) -> customerEmitters.remove(orderId));

        customerEmitters.put(orderId, emitter);
        
        // Send initial heartbeat to establish stream immediately
        try {
            emitter.send(SseEmitter.event().name("init").data("Connected"));
        } catch (IOException e) {
            customerEmitters.remove(orderId);
        }

        return emitter;
    }

    /**
     * Subscribe restaurant dashboard (Admin/Kitchen) to all updates of the restaurant.
     */
    public SseEmitter subscribeToRestaurant(Long restaurantId) {
        SseEmitter emitter = new SseEmitter(RESTAURANT_TIMEOUT);

        restaurantEmitters.computeIfAbsent(restaurantId, k -> new CopyOnWriteArrayList<>());
        restaurantEmitters.get(restaurantId).add(emitter);

        emitter.onCompletion(() -> removeRestaurantEmitter(restaurantId, emitter));
        emitter.onTimeout(() -> removeRestaurantEmitter(restaurantId, emitter));
        emitter.onError((e) -> removeRestaurantEmitter(restaurantId, emitter));

        // Send initial heartbeat
        try {
            emitter.send(SseEmitter.event().name("init").data("Connected"));
        } catch (IOException e) {
            removeRestaurantEmitter(restaurantId, emitter);
        }

        return emitter;
    }

    private void removeRestaurantEmitter(Long restaurantId, SseEmitter emitter) {
        List<SseEmitter> list = restaurantEmitters.get(restaurantId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                restaurantEmitters.remove(restaurantId);
            }
        }
    }

    /**
     * Listens to order update events published by OrderServiceImpl.
     */
    @EventListener
    public void handleOrderUpdateEvent(OrderUpdateEvent event) {
        OrderResponse order = event.getOrder();
        if (order.getStatus() == com.kitchen.order.enums.OrderStatus.PAYMENT_PENDING) {
            log.info("SSE Stream Skipping broadcast for orderId={} as status is PAYMENT_PENDING", order.getOrderId());
            return;
        }
        log.info("SSE Stream Broadcasting status update for orderId={}, status={}", order.getOrderId(), order.getStatus());

        // 1. Notify the individual customer tracking screen
        SseEmitter customerEmitter = customerEmitters.get(order.getOrderId());
        if (customerEmitter != null) {
            try {
                customerEmitter.send(SseEmitter.event()
                        .name("status-change")
                        .data(order));
            } catch (IOException e) {
                log.warn("Failed sending update to customer emitter for order {}: {}", order.getOrderId(), e.getMessage());
                customerEmitters.remove(order.getOrderId());
            }
        }

        // 2. Notify all connected restaurant staff dashboards (Admin / Kitchen)
        List<SseEmitter> list = restaurantEmitters.get(order.getRestaurantId());
        if (list != null && !list.isEmpty()) {
            List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("order-update")
                            .data(order));
                } catch (IOException e) {
                    log.warn("Failed sending update to restaurant emitter {}: {}", order.getRestaurantId(), e.getMessage());
                    deadEmitters.add(emitter);
                }
            }
            list.removeAll(deadEmitters);
            if (list.isEmpty()) {
                restaurantEmitters.remove(order.getRestaurantId());
            }
        }
    }
}
