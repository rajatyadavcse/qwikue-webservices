package com.kitchen.order.controller;

import com.kitchen.order.dto.request.CreateOrderRequest;
import com.kitchen.order.dto.request.OrderDiscountRequest;
import com.kitchen.order.dto.request.UpdateOrderRequest;
import com.kitchen.order.dto.request.UpdateOrderStatusRequest;
import com.kitchen.order.dto.response.OrderItemResponse;
import com.kitchen.order.dto.response.OrderResponse;
import com.kitchen.order.dto.response.PagedResponse;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.service.IOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.kitchen.order.service.OrderStreamService;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Order management endpoints")
public class OrderController {

    @Autowired
    private IOrderService orderService;

    @Autowired
    private OrderStreamService streamService;

    // ── POST /orders ───────────────────────────────────────────────────────────

    @Operation(
            summary = "Create a new order",
            description = "Creates an order after validating restaurant, table, and all menu items. " +
                          "Menu item prices are fetched from restaurant-service and snapshotted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed or menu item unavailable"),
            @ApiResponse(responseCode = "404", description = "Restaurant, table, or menu item not found"),
            @ApiResponse(responseCode = "503", description = "restaurant-service is unavailable")
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return new ResponseEntity<>(orderService.createOrder(request), HttpStatus.CREATED);
    }
    // ── GET /orders/current?restaurantId=&entityNo= ───────────────────────────
 
    @Operation(
            summary = "Get current active orders for a restaurant",
            description = "Returns the currently active orders (PENDING, PREPARING, or READY) for a given restaurantId. If entityNo is provided, returns the active order for that entity. If entityNo is omitted, returns all active orders for the restaurant. Returns empty list if none found."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of active orders (empty if none active)")
    })
    @GetMapping(value = "/current", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OrderResponse>> getCurrentOrders(
            @Parameter(description = "Restaurant ID", required = true)
            @RequestParam Long restaurantId,

            @Parameter(description = "Entity/Table Number", required = false)
            @RequestParam(required = false) String entityNo) {
        return ResponseEntity.ok(orderService.getCurrentOrders(restaurantId, entityNo));
    }

    // ── GET /orders/{id} ──────────────────────────────────────────────────────

    @Operation(summary = "Get order by ID", description = "Returns a single order with all its items.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "Order ID") @PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    // ── PUT /orders/{id} ──────────────────────────────────────────────────────

    @Operation(
            summary = "Update an order",
            description = "Updates order details such as items, table/entity, customer details, notes, payment mode, or discount. " +
                          "Recalculates totals and charges if items or discounts are modified. Only non-completed/non-cancelled orders can be updated."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or order is in a non-modifiable state"),
            @ApiResponse(responseCode = "404", description = "Order or referenced entity/menu item not found")
    })
    @PutMapping(value = "/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> updateOrder(
            @Parameter(description = "Order ID") @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request) {
        return ResponseEntity.ok(orderService.updateOrder(id, request));
    }

    // ── GET /orders?restaurantId=&status=&page=&size= ─────────────────────────

    @Operation(
            summary = "List orders for a restaurant",
            description = "Returns paginated orders for a restaurant. Optionally filter by status and date range (fromDate/toDate, inclusive, yyyy-MM-dd in Asia/Kolkata timezone). " +
                          "Default: page=0, size=20, sorted by createdAt descending."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders returned (may be empty)")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedResponse<OrderResponse>> getOrdersByRestaurant(
            @Parameter(description = "Restaurant ID", required = true)
            @RequestParam Long restaurantId,

            @Parameter(description = "Filter by order status (optional)")
            @RequestParam(required = false) OrderStatus status,

            @Parameter(description = "Filter from date (inclusive, yyyy-MM-dd, Asia/Kolkata)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "Filter to date (inclusive, yyyy-MM-dd, Asia/Kolkata)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(orderService.getOrdersByRestaurant(restaurantId, status, fromDate, toDate, pageable));
    }

    // ── PUT /orders/{id}/status ───────────────────────────────────────────────

    @Operation(
            summary = "Update order status",
            description = "Transitions the order to a new status or updates order status fields (reason, prepMinutes, subPaymentMode). " +
                          "If status is unchanged, field updates are still allowed."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status or order fields updated successfully"),
            @ApiResponse(responseCode = "400", description = "Null status or identical status transition without field updates"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PutMapping(value = "/{id}/status",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @Parameter(description = "Order ID") @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request));
    }

    // ── DELETE /orders/{id} (soft cancel) ────────────────────────────────────

    @Operation(
            summary = "Cancel an order",
            description = "Soft-cancels an order by setting status to CANCELLED. " +
                          "Only PAYMENT_PENDING, PENDING and PREPARING orders can be cancelled. Reason is mandatory."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled"),
            @ApiResponse(responseCode = "400", description = "Reason is missing or blank"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "422", description = "Order is already COMPLETED or CANCELLED")
    })
    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> cancelOrder(
            @Parameter(description = "Order ID") @PathVariable Long id,
            @Parameter(description = "Cancellation reason") @RequestParam String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(id, reason));
    }

    // ── GET /orders/{id}/items ────────────────────────────────────────────────

    @Operation(
            summary = "Get items for an order",
            description = "Returns all line items for a specific order."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Items returned"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping(value = "/{id}/items", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OrderItemResponse>> getOrderItems(
            @Parameter(description = "Order ID") @PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderItems(id));
    }

    // ── GET /orders/statuses (utility — return all valid statuses) ────────────

    @Operation(summary = "List all possible order statuses", description = "Utility endpoint for UI dropdowns.")
    @GetMapping(value = "/statuses", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String[]>> getStatuses() {
        String[] statuses = java.util.Arrays.stream(OrderStatus.values())
                .map(Enum::name)
                .toArray(String[]::new);
        return ResponseEntity.ok(Map.of("statuses", statuses));
    }

    // ── GET /orders/types (utility — return all valid order types) ───────────────

    @Operation(summary = "List all possible order types", description = "Utility endpoint for UI dropdowns/options (e.g. DINE_IN, TAKE_AWAY).")
    @GetMapping(value = "/types", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String[]>> getOrderTypes() {
        String[] types = java.util.Arrays.stream(com.kitchen.order.enums.OrderType.values())
                .map(Enum::name)
                .toArray(String[]::new);
        return ResponseEntity.ok(Map.of("types", types));
    }

    // ── GET /orders/{id}/stream (customer SSE tracking stream) ─────────────────

    @Operation(summary = "Stream order status updates for customer", description = "Standard HTTP-based SSE stream.")
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderUpdates(
            @Parameter(description = "Order ID") @PathVariable Long id) {
        return streamService.subscribeToOrder(id);
    }

    // ── PUT /orders/{id}/discount ─────────────────────────────────────────────

    @Operation(
            summary = "Apply or update order-level discount",
            description = "Applies or updates an order-level discount (PERCENTAGE or FIXED) on an active unpaid order and recalculates total amounts."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Discount applied successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid discount parameters or order is in non-modifiable state"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PutMapping(value = "/{id}/discount",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> applyOrderDiscount(
            @Parameter(description = "Order ID") @PathVariable Long id,
            @Valid @RequestBody OrderDiscountRequest request) {
        return ResponseEntity.ok(orderService.applyOrderDiscount(id, request));
    }

    // ── DELETE /orders/{id}/discount ──────────────────────────────────────────

    @Operation(
            summary = "Remove order-level discount",
            description = "Removes the order-level discount from an active unpaid order and recalculates total amounts."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Discount removed successfully"),
            @ApiResponse(responseCode = "400", description = "Order is in non-modifiable state"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @DeleteMapping(value = "/{id}/discount", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> removeOrderDiscount(
            @Parameter(description = "Order ID") @PathVariable Long id) {
        return ResponseEntity.ok(orderService.removeOrderDiscount(id));
    }

    // ── GET /orders/restaurant/{restaurantId}/stream (staff dashboard stream) ──

    @Operation(summary = "Stream all restaurant order updates for dashboards", description = "Standard HTTP-based SSE stream.")
    @GetMapping(value = "/restaurant/{restaurantId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRestaurantUpdates(
            @Parameter(description = "Restaurant ID") @PathVariable Long restaurantId) {
        return streamService.subscribeToRestaurant(restaurantId);
    }
}
