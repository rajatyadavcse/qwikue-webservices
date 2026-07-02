package com.kitchen.order.service;

import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.dto.request.CreateOrderRequest;
import com.kitchen.order.dto.request.OrderItemRequest;
import com.kitchen.order.dto.response.OrderResponse;
import com.kitchen.order.dto.response.RestaurantChargeDto;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.enums.PaymentStatus;
import com.kitchen.order.enums.OrderedBy;
import com.kitchen.order.mapper.OrderMapper;
import com.kitchen.order.dao.CustomerDAO;
import com.kitchen.order.repository.CustomerRepository;
import com.kitchen.order.repository.OrderRepository;
import com.kitchen.order.repository.RestaurantTokenCounterRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.time.LocalDate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private RestaurantTokenCounterRepository tokenCounterRepository;

    @Mock
    private RestaurantValidationService validationService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IPaymentService paymentService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    public void testCreateOrderCalculatesTaxesAndServiceChargesCorrectly() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setEntityNo("10");
        request.setNotes("No onions");
        request.setPaymentMode(PaymentMode.CASH);
        request.setCustomerName("John Doe");
        request.setPhone("9876543210");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(2);
        request.setItems(Collections.singletonList(itemRequest));

        // Mock Restaurant details with dynamic charges (CGST 2.5%, SGST 2.5%, Service Charge 10.0%)
        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setRestaurantName("Tasty Restaurant");
        restaurant.setStatus("ACTIVE");

        List<RestaurantChargeDto> charges = new ArrayList<>();
        charges.add(new RestaurantChargeDto("CGST", "PERCENTAGE", new BigDecimal("2.5"), "TAX"));
        charges.add(new RestaurantChargeDto("SGST", "PERCENTAGE", new BigDecimal("2.5"), "TAX"));
        charges.add(new RestaurantChargeDto("Service Charge", "PERCENTAGE", new BigDecimal("10.0"), "SERVICE_CHARGE"));
        restaurant.setTaxesAndCharges(charges);

        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        // Mock Entity validation
        RestaurantValidationService.EntityResponse entity = new RestaurantValidationService.EntityResponse();
        entity.setEntityNo("10");
        entity.setRestaurantId(1L);
        entity.setStatus("ACTIVE");
        entity.setOrderEntityType("DINE_IN");
        when(validationService.validateEntity("10", 1L)).thenReturn(entity);

        // Mock Menu price fetching: unit price of $50.00
        RestaurantValidationService.MenuResponse menu = new RestaurantValidationService.MenuResponse();
        menu.setMenuId(101L);
        menu.setItemName("Pizza");
        menu.setPrice(new BigDecimal("50.00"));
        menu.setIsAvailable(true);
        when(validationService.validateMenuAndGetPrice(101L)).thenReturn(menu);

        // Mock customer validation/saving
        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Mock token counter repository getNextTokenNo
        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(5);

        // Mock orderRepository save
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO order = invocation.getArgument(0);
            order.setOrderId(123L); // assign a mock ID
            return order;
        });

        // Mock mapper mapping
        OrderResponse mockResponse = new OrderResponse();
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            mockResponse.setOrderId(dao.getOrderId());
            mockResponse.setSubTotal(dao.getSubTotal());
            mockResponse.setTaxAmount(dao.getTaxAmount());
            mockResponse.setServiceChargeAmount(dao.getServiceChargeAmount());
            mockResponse.setDiscountAmount(dao.getDiscountAmount());
            mockResponse.setTotalAmount(dao.getTotalAmount());
            mockResponse.setTaxesAndCharges(dao.getTaxesAndCharges());
            mockResponse.setOrderEntityType(dao.getOrderEntityType());
            mockResponse.setTokenNo(dao.getTokenNo());
            mockResponse.setStatus(dao.getStatus());
            mockResponse.setPaymentMode(dao.getPaymentMode());
            return mockResponse;
        });

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        // subTotal = 2 * 50.00 = 100.00
        assertEquals(new BigDecimal("100.00"), response.getSubTotal());

        // CGST = 100.00 * 2.5% = 2.50, SGST = 100.00 * 2.5% = 2.50, Total Tax = 5.00
        assertEquals(new BigDecimal("5.00"), response.getTaxAmount());

        // Service Charge = 100.00 * 10% = 10.00
        assertEquals(new BigDecimal("10.00"), response.getServiceChargeAmount());

        // Total payable amount = 100.00 + 5.00 + 10.00 = 115.00
        assertEquals(new BigDecimal("115.00"), response.getTotalAmount());

        // Snapshot details count check
        assertEquals(3, response.getTaxesAndCharges().size());
        assertEquals("CGST", response.getTaxesAndCharges().get(0).getName());
        assertEquals(new BigDecimal("2.50"), response.getTaxesAndCharges().get(0).getCalculatedAmount());

        // Verify orderEntityType
        assertEquals("DINE_IN", response.getOrderEntityType());

        // Verify tokenNo
        assertEquals(5, response.getTokenNo());
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertEquals(PaymentMode.CASH, response.getPaymentMode());

        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testCreateOnlineOrderDoesNotGenerateTokenAndSetsPaymentPending() throws Exception {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setEntityNo("10");
        request.setNotes("No onions");
        request.setPaymentMode(PaymentMode.ONLINE);
        request.setCustomerName("John Doe");
        request.setPhone("9876543210");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(2);
        request.setItems(Collections.singletonList(itemRequest));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setRestaurantName("Tasty Restaurant");
        restaurant.setStatus("ACTIVE");
        restaurant.setRazorpayKeyId("key_123");
        restaurant.setRazorpayKeySecret("secret_123");
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        RestaurantValidationService.EntityResponse entity = new RestaurantValidationService.EntityResponse();
        entity.setEntityNo("10");
        entity.setRestaurantId(1L);
        entity.setStatus("ACTIVE");
        entity.setOrderEntityType("DINE_IN");
        when(validationService.validateEntity("10", 1L)).thenReturn(entity);

        RestaurantValidationService.MenuResponse menu = new RestaurantValidationService.MenuResponse();
        menu.setMenuId(101L);
        menu.setItemName("Pizza");
        menu.setPrice(new BigDecimal("50.00"));
        menu.setIsAvailable(true);
        when(validationService.validateMenuAndGetPrice(101L)).thenReturn(menu);

        // Mock customer validation/saving
        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO order = invocation.getArgument(0);
            order.setOrderId(123L);
            return order;
        });

        when(paymentService.createOrder(eq(123L), any(BigDecimal.class), eq("key_123"), eq("secret_123")))
                .thenReturn("razorpay_order_id_test");

        OrderResponse mockResponse = new OrderResponse();
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            mockResponse.setOrderId(dao.getOrderId());
            mockResponse.setPaymentMode(dao.getPaymentMode());
            mockResponse.setStatus(dao.getStatus());
            mockResponse.setTokenNo(dao.getTokenNo());
            return mockResponse;
        });

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertEquals(PaymentMode.ONLINE, response.getPaymentMode());
        assertEquals(OrderStatus.PAYMENT_PENDING, response.getStatus());
        assertNull(response.getTokenNo());

        // Verify token generation was NOT called
        verify(tokenCounterRepository, never()).getNextTokenNo(anyLong(), any(LocalDate.class));
        // Verify payment service was called
        verify(paymentService, times(1)).createOrder(eq(123L), any(BigDecimal.class), eq("key_123"), eq("secret_123"));
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testCompletePaymentTransitionsToPendingAndGeneratesToken() {
        // Arrange
        OrderDAO order = new OrderDAO();
        order.setOrderId(123L);
        order.setRestaurantId(1L);
        order.setPaymentMode(PaymentMode.ONLINE);
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);

        when(orderRepository.findById(123L)).thenReturn(java.util.Optional.of(order));
        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(15);
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse mockResponse = new OrderResponse();
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            mockResponse.setOrderId(dao.getOrderId());
            mockResponse.setPaymentMode(dao.getPaymentMode());
            mockResponse.setStatus(dao.getStatus());
            mockResponse.setTokenNo(dao.getTokenNo());
            mockResponse.setPaymentStatus(dao.getPaymentStatus());
            mockResponse.setRazorpayPaymentId(dao.getRazorpayPaymentId());
            return mockResponse;
        });

        // Act
        OrderResponse response = orderService.completePayment(123L, "pay_payment123");

        // Assert
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertEquals(PaymentStatus.COMPLETED, response.getPaymentStatus());
        assertEquals(15, response.getTokenNo());
        assertEquals("pay_payment123", response.getRazorpayPaymentId());

        verify(tokenCounterRepository, times(1)).getNextTokenNo(eq(1L), any(LocalDate.class));
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testCreateOrderCalculatesDiscountsCorrectly() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setEntityNo("10");
        request.setNotes("With discounts");
        request.setPaymentMode(PaymentMode.CASH);
        request.setCustomerName("John Doe");
        request.setPhone("9876543210");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(2);
        request.setItems(Collections.singletonList(itemRequest));

        // Mock Restaurant details with dynamic charges (CGST 2.5%, Flat Discount 15.00, 10% Discount)
        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setRestaurantName("Tasty Restaurant");
        restaurant.setStatus("ACTIVE");

        List<RestaurantChargeDto> charges = new ArrayList<>();
        charges.add(new RestaurantChargeDto("CGST", "PERCENTAGE", new BigDecimal("2.5"), "TAX"));
        charges.add(new RestaurantChargeDto("Flat Discount", "FIXED", new BigDecimal("15.00"), "DISCOUNT"));
        charges.add(new RestaurantChargeDto("Seasonal Discount", "PERCENTAGE", new BigDecimal("10.0"), "DISCOUNT"));
        restaurant.setTaxesAndCharges(charges);

        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        // Mock Entity validation
        RestaurantValidationService.EntityResponse entity = new RestaurantValidationService.EntityResponse();
        entity.setEntityNo("10");
        entity.setRestaurantId(1L);
        entity.setStatus("ACTIVE");
        entity.setOrderEntityType("DINE_IN");
        when(validationService.validateEntity("10", 1L)).thenReturn(entity);

        // Mock Menu price fetching: unit price of $50.00
        RestaurantValidationService.MenuResponse menu = new RestaurantValidationService.MenuResponse();
        menu.setMenuId(101L);
        menu.setItemName("Pizza");
        menu.setPrice(new BigDecimal("50.00"));
        menu.setIsAvailable(true);
        when(validationService.validateMenuAndGetPrice(101L)).thenReturn(menu);

        // Mock customer validation/saving
        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Mock token counter repository getNextTokenNo
        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(5);

        // Mock orderRepository save
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO order = invocation.getArgument(0);
            order.setOrderId(123L);
            return order;
        });

        // Mock mapper mapping
        OrderResponse mockResponse = new OrderResponse();
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            mockResponse.setOrderId(dao.getOrderId());
            mockResponse.setSubTotal(dao.getSubTotal());
            mockResponse.setTaxAmount(dao.getTaxAmount());
            mockResponse.setServiceChargeAmount(dao.getServiceChargeAmount());
            mockResponse.setDiscountAmount(dao.getDiscountAmount());
            mockResponse.setTotalAmount(dao.getTotalAmount());
            mockResponse.setTaxesAndCharges(dao.getTaxesAndCharges());
            mockResponse.setOrderEntityType(dao.getOrderEntityType());
            mockResponse.setTokenNo(dao.getTokenNo());
            mockResponse.setStatus(dao.getStatus());
            mockResponse.setPaymentMode(dao.getPaymentMode());
            return mockResponse;
        });

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        // subTotal = 2 * 50.00 = 100.00
        assertEquals(new BigDecimal("100.00"), response.getSubTotal());

        // CGST = 100.00 * 2.5% = 2.50
        assertEquals(new BigDecimal("2.50"), response.getTaxAmount());
        assertEquals(BigDecimal.ZERO, response.getServiceChargeAmount());

        // Discounts:
        // Flat Discount = 15.00
        // Seasonal Discount = 100.00 * 10% = 10.00
        // Total Discount = 25.00
        assertEquals(new BigDecimal("25.00"), response.getDiscountAmount());

        // Total payable amount = 100.00 (subtotal) + 2.50 (tax) - 25.00 (discount) = 77.50
        assertEquals(new BigDecimal("77.50"), response.getTotalAmount());

        // Snapshot details count check
        assertEquals(3, response.getTaxesAndCharges().size());
        assertEquals("Flat Discount", response.getTaxesAndCharges().get(1).getName());
        assertEquals("DISCOUNT", response.getTaxesAndCharges().get(1).getCategory());
        assertEquals(new BigDecimal("15.00"), response.getTaxesAndCharges().get(1).getCalculatedAmount());
        assertEquals("Seasonal Discount", response.getTaxesAndCharges().get(2).getName());
        assertEquals("DISCOUNT", response.getTaxesAndCharges().get(2).getCategory());
        assertEquals(new BigDecimal("10.00"), response.getTaxesAndCharges().get(2).getCalculatedAmount());

        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testUpdateOrderStatusToCompletedForCashOrderSetsPaymentCompleted() {
        // Arrange
        OrderDAO order = new OrderDAO();
        order.setOrderId(123L);
        order.setRestaurantId(1L);
        order.setPaymentMode(PaymentMode.CASH);
        order.setStatus(OrderStatus.READY);
        order.setPaymentStatus(PaymentStatus.PENDING);

        when(orderRepository.findById(123L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse mockResponse = new OrderResponse();
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            mockResponse.setOrderId(dao.getOrderId());
            mockResponse.setPaymentMode(dao.getPaymentMode());
            mockResponse.setStatus(dao.getStatus());
            mockResponse.setPaymentStatus(dao.getPaymentStatus());
            return mockResponse;
        });

        com.kitchen.order.dto.request.UpdateOrderStatusRequest request = new com.kitchen.order.dto.request.UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);

        // Act
        OrderResponse response = orderService.updateOrderStatus(123L, request);

        // Assert
        assertEquals(OrderStatus.COMPLETED, response.getStatus());
        assertEquals(PaymentStatus.COMPLETED, response.getPaymentStatus());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testCreateOrderWithExistingCustomerNameUpdate() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setEntityNo("10");
        request.setNotes("Existing customer");
        request.setPaymentMode(PaymentMode.CASH);
        request.setCustomerName("Jane Doe"); // New name for same phone
        request.setPhone("9876543210");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(2);
        request.setItems(Collections.singletonList(itemRequest));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setStatus("ACTIVE");
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        RestaurantValidationService.EntityResponse entity = new RestaurantValidationService.EntityResponse();
        entity.setEntityNo("10");
        entity.setRestaurantId(1L);
        entity.setOrderEntityType("DINE_IN");
        when(validationService.validateEntity("10", 1L)).thenReturn(entity);

        RestaurantValidationService.MenuResponse menu = new RestaurantValidationService.MenuResponse();
        menu.setMenuId(101L);
        menu.setPrice(new BigDecimal("50.00"));
        menu.setIsAvailable(true);
        when(validationService.validateMenuAndGetPrice(101L)).thenReturn(menu);

        CustomerDAO existingCustomer = new CustomerDAO();
        existingCustomer.setCustomerId(1L);
        existingCustomer.setCustomerName("John Doe"); // Old name
        existingCustomer.setPhone("9876543210");

        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(5);
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse mockResponse = new OrderResponse();
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            mockResponse.setOrderId(dao.getOrderId());
            mockResponse.setTotalAmount(dao.getTotalAmount());
            return mockResponse;
        });

        // Act
        orderService.createOrder(request);

        // Assert
        verify(customerRepository, times(1)).save(argThat(customer -> 
            customer.getCustomerName().equals("Jane Doe") && customer.getPhone().equals("9876543210")
        ));
    }

    @Test
    public void testCreateOrderWithNewCustomerCreation() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setEntityNo("10");
        request.setNotes("New customer");
        request.setPaymentMode(PaymentMode.CASH);
        request.setCustomerName("Alice");
        request.setPhone("5556667777");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(2);
        request.setItems(Collections.singletonList(itemRequest));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setStatus("ACTIVE");
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        RestaurantValidationService.EntityResponse entity = new RestaurantValidationService.EntityResponse();
        entity.setEntityNo("10");
        entity.setRestaurantId(1L);
        entity.setOrderEntityType("DINE_IN");
        when(validationService.validateEntity("10", 1L)).thenReturn(entity);

        RestaurantValidationService.MenuResponse menu = new RestaurantValidationService.MenuResponse();
        menu.setMenuId(101L);
        menu.setPrice(new BigDecimal("50.00"));
        menu.setIsAvailable(true);
        when(validationService.validateMenuAndGetPrice(101L)).thenReturn(menu);

        when(customerRepository.findByPhone("5556667777")).thenReturn(Optional.empty());
        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(5);
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse mockResponse = new OrderResponse();
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            mockResponse.setOrderId(dao.getOrderId());
            return mockResponse;
        });

        // Act
        orderService.createOrder(request);

        // Assert
        verify(customerRepository, times(1)).save(argThat(customer -> 
            customer.getCustomerName().equals("Alice") && customer.getPhone().equals("5556667777")
        ));
    }

    @Test
    public void testGetOrdersByRestaurant_NoDateRange_WithStatus() {
        // Arrange
        Long restaurantId = 1L;
        OrderStatus status = OrderStatus.PENDING;
        Pageable pageable = PageRequest.of(0, 10);
        
        List<OrderDAO> orders = List.of(new OrderDAO());
        Page<OrderDAO> page = new PageImpl<>(orders, pageable, 1);
        
        when(orderRepository.findByRestaurantIdAndStatus(restaurantId, status, pageable)).thenReturn(page);
        when(orderMapper.orderDAOListToResponseList(any())).thenReturn(List.of(new OrderResponse()));
        
        // Act
        var response = orderService.getOrdersByRestaurant(restaurantId, status, null, null, pageable);
        
        // Assert
        verify(orderRepository, times(1)).findByRestaurantIdAndStatus(restaurantId, status, pageable);
        assertEquals(1, response.getContent().size());
    }

    @Test
    public void testGetOrdersByRestaurant_NoDateRange_NoStatus() {
        // Arrange
        Long restaurantId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        
        List<OrderDAO> orders = List.of(new OrderDAO());
        Page<OrderDAO> page = new PageImpl<>(orders, pageable, 1);
        
        when(orderRepository.findByRestaurantIdAndStatusNot(restaurantId, OrderStatus.PAYMENT_PENDING, pageable)).thenReturn(page);
        when(orderMapper.orderDAOListToResponseList(any())).thenReturn(List.of(new OrderResponse()));
        
        // Act
        var response = orderService.getOrdersByRestaurant(restaurantId, null, null, null, pageable);
        
        // Assert
        verify(orderRepository, times(1)).findByRestaurantIdAndStatusNot(restaurantId, OrderStatus.PAYMENT_PENDING, pageable);
        assertEquals(1, response.getContent().size());
    }

    @Test
    public void testGetOrdersByRestaurant_WithDateRange_WithStatus() {
        // Arrange
        Long restaurantId = 1L;
        OrderStatus status = OrderStatus.PENDING;
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        Pageable pageable = PageRequest.of(0, 10);
        
        List<OrderDAO> orders = List.of(new OrderDAO());
        Page<OrderDAO> page = new PageImpl<>(orders, pageable, 1);
        
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        
        when(orderRepository.findByRestaurantIdAndStatusAndDateRange(restaurantId, status, start, end, pageable)).thenReturn(page);
        when(orderMapper.orderDAOListToResponseList(any())).thenReturn(List.of(new OrderResponse()));
        
        // Act
        var response = orderService.getOrdersByRestaurant(restaurantId, status, from, to, pageable);
        
        // Assert
        verify(orderRepository, times(1)).findByRestaurantIdAndStatusAndDateRange(restaurantId, status, start, end, pageable);
        assertEquals(1, response.getContent().size());
    }

    @Test
    public void testGetOrdersByRestaurant_WithDateRange_NoStatus() {
        // Arrange
        Long restaurantId = 1L;
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        Pageable pageable = PageRequest.of(0, 10);
        
        List<OrderDAO> orders = List.of(new OrderDAO());
        Page<OrderDAO> page = new PageImpl<>(orders, pageable, 1);
        
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        
        when(orderRepository.findByRestaurantIdAndStatusNotAndDateRange(restaurantId, OrderStatus.PAYMENT_PENDING, start, end, pageable)).thenReturn(page);
        when(orderMapper.orderDAOListToResponseList(any())).thenReturn(List.of(new OrderResponse()));
        
        // Act
        var response = orderService.getOrdersByRestaurant(restaurantId, null, from, to, pageable);
        
        // Assert
        verify(orderRepository, times(1)).findByRestaurantIdAndStatusNotAndDateRange(restaurantId, OrderStatus.PAYMENT_PENDING, start, end, pageable);
        assertEquals(1, response.getContent().size());
    }

    @Test
    public void testGetOrdersByRestaurant_PaymentPendingStatusReturnsEmptyPage() {
        // Arrange
        Long restaurantId = 1L;
        OrderStatus status = OrderStatus.PAYMENT_PENDING;
        Pageable pageable = PageRequest.of(0, 10);
        
        // Act
        var response = orderService.getOrdersByRestaurant(restaurantId, status, null, null, pageable);
        
        // Assert
        verifyNoInteractions(orderRepository);
        assertEquals(0, response.getContent().size());
    }

    @Test
    public void testCreateOrderSavesAndReturnsOrderedBy() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setEntityNo("10");
        request.setNotes("Ordered by ADMIN test");
        request.setPaymentMode(PaymentMode.CASH);
        request.setCustomerName("Jane Doe");
        request.setPhone("9876543211");
        request.setOrderedBy(OrderedBy.ADMIN);

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(1);
        request.setItems(Collections.singletonList(itemRequest));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setStatus("ACTIVE");
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        RestaurantValidationService.EntityResponse entity = new RestaurantValidationService.EntityResponse();
        entity.setEntityNo("10");
        when(validationService.validateEntity("10", 1L)).thenReturn(entity);

        CustomerDAO customer = new CustomerDAO();
        customer.setCustomerName("Jane Doe");
        customer.setPhone("9876543211");
        when(customerRepository.findByPhone("9876543211")).thenReturn(Optional.of(customer));

        RestaurantValidationService.MenuResponse menu = new RestaurantValidationService.MenuResponse();
        menu.setMenuId(101L);
        menu.setPrice(new BigDecimal("10.0"));
        menu.setItemName("Mock Burger");
        when(validationService.validateMenuAndGetPrice(101L)).thenReturn(menu);

        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(1);

        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO order = invocation.getArgument(0);
            order.setOrderId(789L);
            return order;
        });

        OrderResponse mockResponse = new OrderResponse();
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            mockResponse.setOrderId(dao.getOrderId());
            mockResponse.setOrderedBy(dao.getOrderedBy());
            return mockResponse;
        });

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertEquals(OrderedBy.ADMIN, response.getOrderedBy());
        verify(orderRepository).save(argThat(order -> order.getOrderedBy() == OrderedBy.ADMIN));
    }
}

