package com.kitchen.order.controller;

import com.kitchen.order.dto.response.OrderResponse;
import com.kitchen.order.service.IOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/kitchen")
@Tag(name = "Kitchen", description = "Kitchen dashboard endpoints")
public class KitchenController {

    @Autowired
    private IOrderService orderService;

    // ── GET /kitchen/orders?restaurantId= ────────────────────────────────────

    @Operation(
            summary = "Get active kitchen orders for a restaurant",
            description = "Returns all non-terminal orders (PENDING, PREPARING, READY) for the specified restaurant. " +
                          "Intended for the real-time kitchen dashboard. " +
                          "Returns newest orders first (sorted by createdAt ascending so kitchen sees oldest unhandled orders first)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active orders returned (may be empty)")
    })
    @GetMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OrderResponse>> getKitchenOrders(
            @Parameter(description = "Restaurant ID — required to scope kitchen feed", required = true)
            @RequestParam Long restaurantId) {
        return ResponseEntity.ok(orderService.getKitchenOrders(restaurantId));
    }
}
