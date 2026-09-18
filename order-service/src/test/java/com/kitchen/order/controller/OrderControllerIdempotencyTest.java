package com.kitchen.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchen.order.dto.request.UpdateOrderRequest;
import com.kitchen.order.dto.response.OrderResponse;
import com.kitchen.order.enums.OrderType;
import com.kitchen.order.service.IOrderService;
import com.kitchen.order.service.OrderStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kitchen.order.aspect.IdempotencyAspect;
import com.kitchen.order.service.IdempotencyService;
import org.springframework.context.annotation.Import;

@WebMvcTest(controllers = OrderController.class)
@Import({IdempotencyAspect.class, IdempotencyService.class})
@EnableAspectJAutoProxy
class OrderControllerIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IOrderService orderService;

    @MockBean
    private OrderStreamService streamService;

    @Test
    void testUpdateOrderWithSameIdempotencyKeyReturnsCachedResponse() throws Exception {
        Long orderId = 3026L;
        String idempotencyKey = "unique-uuid-12345";

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setOrderType(OrderType.DINE_IN);
        request.setNotes("Extra spicy");

        OrderResponse mockResponse = new OrderResponse();
        mockResponse.setOrderId(orderId);
        mockResponse.setNotes("Extra spicy");

        when(orderService.updateOrder(eq(orderId), any(UpdateOrderRequest.class)))
                .thenReturn(mockResponse);

        // First request: Service method executed
        mockMvc.perform(put("/orders/{id}", orderId)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.notes").value("Extra spicy"));

        verify(orderService, times(1)).updateOrder(eq(orderId), any(UpdateOrderRequest.class));

        // Second request with same Idempotency Key: Returns cached response, service method NOT called again
        mockMvc.perform(put("/orders/{id}", orderId)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Lookup", "HIT"))
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.notes").value("Extra spicy"));

        // Service invocation count remains 1!
        verify(orderService, times(1)).updateOrder(eq(orderId), any(UpdateOrderRequest.class));
    }

    @Test
    void testDifferentEndpointWithSameIdempotencyKeyDoesNotHitCache() throws Exception {
        Long orderId1 = 3026L;
        Long orderId2 = 3027L;
        String idempotencyKey = "shared-uuid-12345";

        UpdateOrderRequest request1 = new UpdateOrderRequest();
        request1.setOrderType(OrderType.DINE_IN);
        request1.setNotes("Extra spicy");

        OrderResponse mockResponse1 = new OrderResponse();
        mockResponse1.setOrderId(orderId1);
        mockResponse1.setNotes("Extra spicy");

        UpdateOrderRequest request2 = new UpdateOrderRequest();
        request2.setOrderType(OrderType.DINE_IN);
        request2.setNotes("No onions");

        OrderResponse mockResponse2 = new OrderResponse();
        mockResponse2.setOrderId(orderId2);
        mockResponse2.setNotes("No onions");

        when(orderService.updateOrder(eq(orderId1), any(UpdateOrderRequest.class)))
                .thenReturn(mockResponse1);
        when(orderService.updateOrder(eq(orderId2), any(UpdateOrderRequest.class)))
                .thenReturn(mockResponse2);

        // Request for order 3026
        mockMvc.perform(put("/orders/{id}", orderId1)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId1));

        // Request for order 3027 with SAME idempotency key (different URI path)
        mockMvc.perform(put("/orders/{id}", orderId2)
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId2))
                .andExpect(header().doesNotExist("X-Cache-Lookup"));

        verify(orderService, times(1)).updateOrder(eq(orderId1), any(UpdateOrderRequest.class));
        verify(orderService, times(1)).updateOrder(eq(orderId2), any(UpdateOrderRequest.class));
    }
}
