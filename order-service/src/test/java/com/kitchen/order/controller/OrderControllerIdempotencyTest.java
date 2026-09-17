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
}
