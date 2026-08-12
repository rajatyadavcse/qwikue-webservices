package com.restaurant.service.service;

import com.restaurant.service.event.OrderEntityUpdateEvent;
import com.restaurant.service.model.OrderEntity;
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
public class OrderEntityStreamService {

    private static final Logger log = LoggerFactory.getLogger(OrderEntityStreamService.class);

    // Map of restaurantId -> List of SseEmitters (Dashboards/staff can have multiple open instances)
    private final Map<Long, List<SseEmitter>> restaurantEmitters = new ConcurrentHashMap<>();

    // Sse Emitter timeout (in milliseconds)
    private static final long RESTAURANT_TIMEOUT = 1_800_000L; // 30 minutes

    /**
     * Subscribe restaurant dashboard/staff to all entity status updates of the restaurant.
     */
    public SseEmitter subscribeToRestaurant(Long restaurantId) {
        SseEmitter emitter = new SseEmitter(RESTAURANT_TIMEOUT);

        restaurantEmitters.computeIfAbsent(restaurantId, k -> new CopyOnWriteArrayList<>());
        restaurantEmitters.get(restaurantId).add(emitter);

        emitter.onCompletion(() -> removeRestaurantEmitter(restaurantId, emitter));
        emitter.onTimeout(() -> removeRestaurantEmitter(restaurantId, emitter));
        emitter.onError((e) -> removeRestaurantEmitter(restaurantId, emitter));

        // Send initial heartbeat to establish stream immediately
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
     * Listens to entity update events published by OrderEntityServiceImpl.
     */
    @EventListener
    public void handleOrderEntityUpdateEvent(OrderEntityUpdateEvent event) {
        if (event == null || event.getOrderEntity() == null) {
            return;
        }

        OrderEntity entity = event.getOrderEntity();
        if (entity.getRestaurantId() == null) {
            return;
        }

        log.info("SSE Stream Broadcasting entity update for restaurantId={}, entityNo={}, status={}",
                entity.getRestaurantId(), entity.getEntityNo(), entity.getStatus());

        List<SseEmitter> list = restaurantEmitters.get(entity.getRestaurantId());
        if (list != null && !list.isEmpty()) {
            List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("entity-update")
                            .data(entity));
                } catch (IOException e) {
                    log.warn("Failed sending update to restaurant entity emitter {}: {}", entity.getRestaurantId(), e.getMessage());
                    deadEmitters.add(emitter);
                }
            }
            list.removeAll(deadEmitters);
            if (list.isEmpty()) {
                restaurantEmitters.remove(entity.getRestaurantId());
            }
        }
    }
}
