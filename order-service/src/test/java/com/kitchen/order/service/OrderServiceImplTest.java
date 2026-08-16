package com.kitchen.order.service;

import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.dao.OrderItemDAO;
import com.kitchen.order.dto.request.CreateOrderRequest;
import com.kitchen.order.dto.request.OrderDiscountRequest;
import com.kitchen.order.dto.request.OrderItemRequest;
import com.kitchen.order.dto.request.UpdateOrderRequest;
import com.kitchen.order.dto.response.OrderResponse;
import com.kitchen.order.dto.response.RestaurantChargeDto;
import com.kitchen.order.enums.DiscountType;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.enums.SubPaymentMode;
import com.kitchen.order.enums.PaymentStatus;
import com.kitchen.order.enums.OrderedBy;
import com.kitchen.order.enums.OrderType;
import com.kitchen.order.mapper.OrderMapper;
import com.kitchen.order.dao.CustomerDAO;
import com.kitchen.order.repository.CustomerRepository;
import com.kitchen.order.repository.OrderRepository;
import com.kitchen.order.repository.RestaurantTokenCounterRepository;
import com.restaurant.service.model.OrderEntityStatus;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private IRestaurantValidationService validationService;

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

        // Mock Restaurant details with dynamic charges (CGST 2.5%, SGST 2.5%, Service
        // Charge 10.0%)
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

        // Mock Restaurant details with dynamic charges (CGST 2.5%, Flat Discount 15.00,
        // 10% Discount)
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

        // Total payable amount = 100.00 (subtotal) + 2.50 (tax) - 25.00 (discount) =
        // 77.50
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
    public void testUpdateOrderStatusWithSubPaymentModeSuccess() {
        // Arrange
        OrderDAO order = new OrderDAO();
        order.setOrderId(123L);
        order.setRestaurantId(1L);
        order.setPaymentMode(PaymentMode.CASH);
        order.setStatus(OrderStatus.READY);
        order.setPaymentStatus(PaymentStatus.PENDING);

        when(orderRepository.findById(123L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            OrderResponse r = new OrderResponse();
            r.setOrderId(dao.getOrderId());
            r.setPaymentMode(dao.getPaymentMode());
            r.setStatus(dao.getStatus());
            r.setPaymentStatus(dao.getPaymentStatus());
            r.setSubPaymentMode(dao.getSubPaymentMode());
            return r;
        });

        com.kitchen.order.dto.request.UpdateOrderStatusRequest request = new com.kitchen.order.dto.request.UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);
        request.setSubPaymentMode(SubPaymentMode.UPI);

        // Act
        OrderResponse response = orderService.updateOrderStatus(123L, request);

        // Assert
        assertEquals(OrderStatus.COMPLETED, response.getStatus());
        assertEquals(SubPaymentMode.UPI, response.getSubPaymentMode());
        assertEquals(SubPaymentMode.UPI, order.getSubPaymentMode());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testUpdateOrderStatusWithSubPaymentModeFailureForOnlineOrder() {
        // Arrange
        OrderDAO order = new OrderDAO();
        order.setOrderId(123L);
        order.setRestaurantId(1L);
        order.setPaymentMode(PaymentMode.ONLINE);
        order.setStatus(OrderStatus.READY);
        order.setPaymentStatus(PaymentStatus.PENDING);

        when(orderRepository.findById(123L)).thenReturn(java.util.Optional.of(order));

        com.kitchen.order.dto.request.UpdateOrderStatusRequest request = new com.kitchen.order.dto.request.UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);
        request.setSubPaymentMode(SubPaymentMode.UPI);

        // Act & Assert
        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> orderService.updateOrderStatus(123L, request)
        );
        assertEquals("subPaymentMode is only allowed when paymentMode is CASH", exception.getMessage());
    }

    @Test
    public void testUpdateOrderStatus_sameStatusCompleted_updatesSubPaymentModeSuccess() {
        // Arrange
        OrderDAO order = new OrderDAO();
        order.setOrderId(123L);
        order.setRestaurantId(1L);
        order.setPaymentMode(PaymentMode.CASH);
        order.setStatus(OrderStatus.COMPLETED);
        order.setPaymentStatus(PaymentStatus.COMPLETED);

        when(orderRepository.findById(123L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            OrderResponse r = new OrderResponse();
            r.setOrderId(dao.getOrderId());
            r.setPaymentMode(dao.getPaymentMode());
            r.setStatus(dao.getStatus());
            r.setPaymentStatus(dao.getPaymentStatus());
            r.setSubPaymentMode(dao.getSubPaymentMode());
            return r;
        });

        com.kitchen.order.dto.request.UpdateOrderStatusRequest request = new com.kitchen.order.dto.request.UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);
        request.setSubPaymentMode(SubPaymentMode.UPI);

        // Act
        OrderResponse response = orderService.updateOrderStatus(123L, request);

        // Assert
        assertEquals(OrderStatus.COMPLETED, response.getStatus());
        assertEquals(SubPaymentMode.UPI, response.getSubPaymentMode());
        assertEquals(SubPaymentMode.UPI, order.getSubPaymentMode());
        verify(orderRepository, times(1)).save(order);
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testUpdateOrderStatus_sameStatusCompleted_updatesReasonSuccess() {
        // Arrange
        OrderDAO order = new OrderDAO();
        order.setOrderId(123L);
        order.setRestaurantId(1L);
        order.setStatus(OrderStatus.COMPLETED);

        when(orderRepository.findById(123L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            OrderResponse r = new OrderResponse();
            r.setOrderId(dao.getOrderId());
            r.setStatus(dao.getStatus());
            r.setReason(dao.getReason());
            return r;
        });

        com.kitchen.order.dto.request.UpdateOrderStatusRequest request = new com.kitchen.order.dto.request.UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);
        request.setReason("Customer payment adjustment");

        // Act
        OrderResponse response = orderService.updateOrderStatus(123L, request);

        // Assert
        assertEquals(OrderStatus.COMPLETED, response.getStatus());
        assertEquals("Customer payment adjustment", response.getReason());
        assertEquals("Customer payment adjustment", order.getReason());
        verify(orderRepository, times(1)).save(order);
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testUpdateOrderStatus_sameStatusNoFields_throwsException() {
        // Arrange
        OrderDAO order = new OrderDAO();
        order.setOrderId(123L);
        order.setRestaurantId(1L);
        order.setStatus(OrderStatus.COMPLETED);

        when(orderRepository.findById(123L)).thenReturn(java.util.Optional.of(order));

        com.kitchen.order.dto.request.UpdateOrderStatusRequest request = new com.kitchen.order.dto.request.UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);

        // Act & Assert
        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> orderService.updateOrderStatus(123L, request)
        );
        assertEquals("Current status and new status are the same", exception.getMessage());
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
        verify(customerRepository, times(1)).save(argThat(
                customer -> customer.getCustomerName().equals("Jane Doe") && customer.getPhone().equals("9876543210")));
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
        verify(customerRepository, times(1)).save(argThat(
                customer -> customer.getCustomerName().equals("Alice") && customer.getPhone().equals("5556667777")));
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

        when(orderRepository.findByRestaurantIdAndStatusNot(restaurantId, OrderStatus.PAYMENT_PENDING, pageable))
                .thenReturn(page);
        when(orderMapper.orderDAOListToResponseList(any())).thenReturn(List.of(new OrderResponse()));

        // Act
        var response = orderService.getOrdersByRestaurant(restaurantId, null, null, null, pageable);

        // Assert
        verify(orderRepository, times(1)).findByRestaurantIdAndStatusNot(restaurantId, OrderStatus.PAYMENT_PENDING,
                pageable);
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

        when(orderRepository.findByRestaurantIdAndStatusAndDateRange(restaurantId, status, start, end, pageable))
                .thenReturn(page);
        when(orderMapper.orderDAOListToResponseList(any())).thenReturn(List.of(new OrderResponse()));

        // Act
        var response = orderService.getOrdersByRestaurant(restaurantId, status, from, to, pageable);

        // Assert
        verify(orderRepository, times(1)).findByRestaurantIdAndStatusAndDateRange(restaurantId, status, start, end,
                pageable);
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

        when(orderRepository.findByRestaurantIdAndStatusNotAndDateRange(restaurantId, OrderStatus.PAYMENT_PENDING,
                start, end, pageable)).thenReturn(page);
        when(orderMapper.orderDAOListToResponseList(any())).thenReturn(List.of(new OrderResponse()));

        // Act
        var response = orderService.getOrdersByRestaurant(restaurantId, null, from, to, pageable);

        // Assert
        verify(orderRepository, times(1)).findByRestaurantIdAndStatusNotAndDateRange(restaurantId,
                OrderStatus.PAYMENT_PENDING, start, end, pageable);
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

    @Test
    public void testCreateOrderThrowsExceptionWhenPaymentModeNotSupportedByRestaurant() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setEntityNo("10");
        request.setCustomerName("John Doe");
        request.setPhone("9876543210");
        request.setPaymentMode(PaymentMode.ONLINE);

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setPaymentModes(List.of(PaymentMode.CASH)); // only CASH supported
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        RestaurantValidationService.EntityResponse entity = new RestaurantValidationService.EntityResponse();
        entity.setEntityNo("10");
        entity.setRestaurantId(1L);
        when(validationService.validateEntity("10", 1L)).thenReturn(entity);

        // Act & Assert
        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request));
        assertEquals("Payment mode ONLINE is not supported by this restaurant", exception.getMessage());
    }

    @Test
    public void testCreateTakeAwayOrderWithoutEntityNoSuccess() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setOrderType(OrderType.TAKE_AWAY);
        request.setEntityNo(null); // No entity/table for take away
        request.setCustomerName("Jane Doe");
        request.setPhone("9876543210");
        request.setPaymentMode(PaymentMode.CASH);

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(1);
        request.setItems(Collections.singletonList(itemRequest));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setRestaurantName("Tasty Takeaway");
        restaurant.setStatus("ACTIVE");
        restaurant.setPaymentModes(List.of(PaymentMode.CASH));
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        RestaurantValidationService.MenuResponse menu = new RestaurantValidationService.MenuResponse();
        menu.setMenuId(101L);
        menu.setItemName("Burger");
        menu.setPrice(new BigDecimal("120.00"));
        menu.setIsAvailable(true);
        when(validationService.validateMenuAndGetPrice(101L)).thenReturn(menu);

        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(12);

        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO order = invocation.getArgument(0);
            order.setOrderId(555L);
            return order;
        });

        OrderResponse mockResponse = new OrderResponse();
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            mockResponse.setOrderId(dao.getOrderId());
            mockResponse.setOrderType(dao.getOrderType());
            mockResponse.setEntityNo(dao.getEntityNo());
            mockResponse.setOrderEntityType(dao.getOrderEntityType());
            mockResponse.setTotalAmount(dao.getTotalAmount());
            mockResponse.setTokenNo(dao.getTokenNo());
            return mockResponse;
        });

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertNotNull(response);
        assertEquals(555L, response.getOrderId());
        assertEquals(OrderType.TAKE_AWAY, response.getOrderType());
        assertNull(response.getEntityNo());
        assertNull(response.getOrderEntityType());
        assertEquals(12, response.getTokenNo());

        // Verify entity validation was never called for TAKE_AWAY without entityNo
        verify(validationService, never()).validateEntity(any(), any());
        verify(orderRepository).save(argThat(order -> order.getOrderType() == OrderType.TAKE_AWAY &&
                order.getEntityNo() == null &&
                order.getOrderEntityType() == null));
    }

    @Test
    public void testCreateDineInOrderWithoutEntityNoThrowsException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setOrderType(OrderType.DINE_IN);
        request.setEntityNo(null); // Missing entityNo
        request.setCustomerName("John Doe");

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request));
        assertEquals("entityNo is required for DINE_IN orders", ex.getMessage());
        verify(validationService, never()).validateEntity(any(), any());
    }

    @Test
    public void testCreateDineInOrderWithBlankEntityNoThrowsException() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setOrderType(OrderType.DINE_IN);
        request.setEntityNo("   "); // Blank entityNo
        request.setCustomerName("John Doe");

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request));
        assertEquals("entityNo is required for DINE_IN orders", ex.getMessage());
        verify(validationService, never()).validateEntity(any(), any());
    }

    @Test
    public void testCreateTakeAwayOrderWithEntityNoSuccess() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setOrderType(OrderType.TAKE_AWAY);
        request.setEntityNo("Counter-1");
        request.setCustomerName("Jane Doe");
        request.setPhone("9876543210");
        request.setPaymentMode(PaymentMode.CASH);

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(1);
        request.setItems(Collections.singletonList(itemRequest));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setPaymentModes(List.of(PaymentMode.CASH));
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        RestaurantValidationService.MenuResponse menu = new RestaurantValidationService.MenuResponse();
        menu.setMenuId(101L);
        menu.setItemName("Burger");
        menu.setPrice(new BigDecimal("120.00"));
        menu.setIsAvailable(true);
        when(validationService.validateMenuAndGetPrice(101L)).thenReturn(menu);

        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(13);

        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO order = invocation.getArgument(0);
            order.setOrderId(556L);
            return order;
        });

        OrderResponse mockResponse = new OrderResponse();
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            mockResponse.setOrderId(dao.getOrderId());
            mockResponse.setOrderType(dao.getOrderType());
            mockResponse.setEntityNo(dao.getEntityNo());
            return mockResponse;
        });

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertEquals(OrderType.TAKE_AWAY, response.getOrderType());
        assertEquals("Counter-1", response.getEntityNo());
        verify(validationService, never()).validateEntity(any(), any());
    }

    @Test
    public void testCreateOrderDefaultsToDineInWhenOrderTypeIsNull() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setOrderType(null); // null orderType -> defaults to DINE_IN
        request.setEntityNo("Table-5");
        request.setCustomerName("Alice");
        request.setPaymentMode(PaymentMode.CASH);

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(1);
        request.setItems(Collections.singletonList(itemRequest));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setPaymentModes(List.of(PaymentMode.CASH));
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        RestaurantValidationService.EntityResponse entity = new RestaurantValidationService.EntityResponse();
        entity.setEntityNo("Table-5");
        entity.setRestaurantId(1L);
        entity.setOrderEntityType("TABLE");
        when(validationService.validateEntity("Table-5", 1L)).thenReturn(entity);

        RestaurantValidationService.MenuResponse menu = new RestaurantValidationService.MenuResponse();
        menu.setMenuId(101L);
        menu.setItemName("Salad");
        menu.setPrice(new BigDecimal("80.00"));
        menu.setIsAvailable(true);
        when(validationService.validateMenuAndGetPrice(101L)).thenReturn(menu);

        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(14);

        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO order = invocation.getArgument(0);
            order.setOrderId(557L);
            return order;
        });

        OrderResponse mockResponse = new OrderResponse();
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            mockResponse.setOrderId(dao.getOrderId());
            mockResponse.setOrderType(dao.getOrderType());
            mockResponse.setEntityNo(dao.getEntityNo());
            mockResponse.setOrderEntityType(dao.getOrderEntityType());
            return mockResponse;
        });

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertEquals(OrderType.DINE_IN, response.getOrderType());
        assertEquals("Table-5", response.getEntityNo());
        assertEquals("TABLE", response.getOrderEntityType());
        verify(validationService, times(1)).validateEntity("Table-5", 1L);
        verify(validationService, times(1)).updateEntityStatus("Table-5", 1L,
                OrderEntityStatus.OCCUPIED);
    }

    @Test
    void updateOrderStatus_ready_setsTableToBillPending() {
        OrderDAO order = new OrderDAO();
        order.setOrderId(100L);
        order.setRestaurantId(1L);
        order.setOrderType(OrderType.DINE_IN);
        order.setEntityNo("Table-1");
        order.setStatus(OrderStatus.PREPARING);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse mockResponse = new OrderResponse();
        mockResponse.setOrderId(100L);
        mockResponse.setStatus(OrderStatus.READY);
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenReturn(mockResponse);

        com.kitchen.order.dto.request.UpdateOrderStatusRequest request = new com.kitchen.order.dto.request.UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.READY);

        orderService.updateOrderStatus(100L, request);

        verify(validationService, times(1)).updateEntityStatus("Table-1", 1L,
                OrderEntityStatus.BILL_PENDING);
    }

    @Test
    void updateOrderStatus_completed_releasesTableWhenNoOtherActiveOrders() {
        OrderDAO order = new OrderDAO();
        order.setOrderId(100L);
        order.setRestaurantId(1L);
        order.setOrderType(OrderType.DINE_IN);
        order.setEntityNo("Table-1");
        order.setStatus(OrderStatus.READY);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.existsByRestaurantIdAndEntityNoAndStatusInAndOrderIdNot(eq(1L), eq("Table-1"), any(),
                eq(100L))).thenReturn(false);

        OrderResponse mockResponse = new OrderResponse();
        mockResponse.setOrderId(100L);
        mockResponse.setStatus(OrderStatus.COMPLETED);
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenReturn(mockResponse);

        com.kitchen.order.dto.request.UpdateOrderStatusRequest request = new com.kitchen.order.dto.request.UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);

        orderService.updateOrderStatus(100L, request);

        verify(validationService, times(1)).updateEntityStatus("Table-1", 1L,
                OrderEntityStatus.AVAILABLE);
    }

    @Test
    void updateOrderStatus_cancelled_releasesTableWhenNoOtherActiveOrders() {
        OrderDAO order = new OrderDAO();
        order.setOrderId(100L);
        order.setRestaurantId(1L);
        order.setOrderType(OrderType.DINE_IN);
        order.setEntityNo("Table-1");
        order.setStatus(OrderStatus.PREPARING);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.existsByRestaurantIdAndEntityNoAndStatusInAndOrderIdNot(eq(1L), eq("Table-1"), any(),
                eq(100L))).thenReturn(false);

        OrderResponse mockResponse = new OrderResponse();
        mockResponse.setOrderId(100L);
        mockResponse.setStatus(OrderStatus.CANCELLED);
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenReturn(mockResponse);

        com.kitchen.order.dto.request.UpdateOrderStatusRequest request = new com.kitchen.order.dto.request.UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CANCELLED);
        request.setReason("Cancelled by customer");

        orderService.updateOrderStatus(100L, request);

        verify(validationService, times(1)).updateEntityStatus("Table-1", 1L,
                OrderEntityStatus.AVAILABLE);
    }

    @Test
    void cancelOrder_releasesTableWhenNoOtherActiveOrders() {
        OrderDAO order = new OrderDAO();
        order.setOrderId(101L);
        order.setRestaurantId(1L);
        order.setOrderType(OrderType.DINE_IN);
        order.setEntityNo("Table-2");
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.existsByRestaurantIdAndEntityNoAndStatusInAndOrderIdNot(eq(1L), eq("Table-2"), any(),
                eq(101L))).thenReturn(false);

        OrderResponse mockResponse = new OrderResponse();
        mockResponse.setOrderId(101L);
        mockResponse.setStatus(OrderStatus.CANCELLED);
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenReturn(mockResponse);

        orderService.cancelOrder(101L, "Customer left");

        verify(validationService, times(1)).updateEntityStatus("Table-2", 1L,
                OrderEntityStatus.AVAILABLE);
    }

    @Test
    void getCurrentOrderByEntity_whenActiveOrderExists_returnsOrderResponse() {
        OrderDAO order = new OrderDAO();
        order.setOrderId(200L);
        order.setRestaurantId(1L);
        order.setEntityNo("T-1");
        order.setStatus(OrderStatus.PREPARING);

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setOrderId(200L);
        expectedResponse.setStatus(OrderStatus.PREPARING);

        when(orderRepository.findFirstByRestaurantIdAndEntityNoAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq("T-1"), eq(List.of(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY))))
                .thenReturn(Optional.of(order));
        when(orderMapper.orderDAOToOrderResponse(order)).thenReturn(expectedResponse);

        OrderResponse result = orderService.getCurrentOrderByEntity(1L, "T-1");

        assertNotNull(result);
        assertEquals(200L, result.getOrderId());
        assertEquals(OrderStatus.PREPARING, result.getStatus());
    }

    @Test
    void getCurrentOrderByEntity_whenNoActiveOrderExists_returnsNull() {
        when(orderRepository.findFirstByRestaurantIdAndEntityNoAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq("T-1"), eq(List.of(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY))))
                .thenReturn(Optional.empty());

        OrderResponse result = orderService.getCurrentOrderByEntity(1L, "T-1");

        assertNull(result);
    }

    @Test
    void getCurrentOrders_whenEntityNoProvidedAndActiveOrderExists_returnsListWithOrder() {
        OrderDAO order = new OrderDAO();
        order.setOrderId(200L);
        order.setRestaurantId(1L);
        order.setEntityNo("T-1");
        order.setStatus(OrderStatus.PREPARING);

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setOrderId(200L);
        expectedResponse.setStatus(OrderStatus.PREPARING);

        when(orderRepository.findFirstByRestaurantIdAndEntityNoAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq("T-1"), eq(List.of(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY))))
                .thenReturn(Optional.of(order));
        when(orderMapper.orderDAOToOrderResponse(order)).thenReturn(expectedResponse);

        List<OrderResponse> result = orderService.getCurrentOrders(1L, "T-1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).getOrderId());
        assertEquals(OrderStatus.PREPARING, result.get(0).getStatus());
    }

    @Test
    void getCurrentOrders_whenEntityNoProvidedAndNoActiveOrderExists_returnsEmptyList() {
        when(orderRepository.findFirstByRestaurantIdAndEntityNoAndStatusInOrderByCreatedAtDesc(
                eq(1L), eq("T-1"), eq(List.of(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY))))
                .thenReturn(Optional.empty());

        List<OrderResponse> result = orderService.getCurrentOrders(1L, "T-1");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCurrentOrders_whenEntityNoNotProvided_returnsAllActiveOrdersForRestaurant() {
        OrderDAO order1 = new OrderDAO();
        order1.setOrderId(101L);
        order1.setRestaurantId(1L);
        order1.setStatus(OrderStatus.PENDING);

        OrderDAO order2 = new OrderDAO();
        order2.setOrderId(102L);
        order2.setRestaurantId(1L);
        order2.setStatus(OrderStatus.PREPARING);

        OrderResponse resp1 = new OrderResponse();
        resp1.setOrderId(101L);
        OrderResponse resp2 = new OrderResponse();
        resp2.setOrderId(102L);

        when(orderRepository.findByRestaurantIdAndStatusIn(
                eq(1L), eq(List.of(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY))))
                .thenReturn(List.of(order1, order2));
        when(orderMapper.orderDAOListToResponseList(List.of(order1, order2)))
                .thenReturn(List.of(resp1, resp2));

        List<OrderResponse> resultNullEntity = orderService.getCurrentOrders(1L, null);
        assertNotNull(resultNullEntity);
        assertEquals(2, resultNullEntity.size());

        List<OrderResponse> resultBlankEntity = orderService.getCurrentOrders(1L, "   ");
        assertNotNull(resultBlankEntity);
        assertEquals(2, resultBlankEntity.size());
    }

    @Test
    void getCurrentOrders_whenRestaurantIdNull_returnsEmptyList() {
        List<OrderResponse> result = orderService.getCurrentOrders(null, "T-1");
        assertNotNull(result);
        assertTrue(result.isEmpty());

        List<OrderResponse> resultNoEntity = orderService.getCurrentOrders(null, null);
        assertNotNull(resultNoEntity);
        assertTrue(resultNoEntity.isEmpty());
    }

    // ── Order Discount Tests ───────────────────────────────────────────────────

    @Test
    public void testCreateOrderWithPercentageOrderDiscount() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setEntityNo("10");
        request.setPaymentMode(PaymentMode.CASH);
        request.setCustomerName("Jane Doe");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(2);
        request.setItems(Collections.singletonList(itemRequest));

        // 10% order discount
        request.setDiscount(
                new OrderDiscountRequest(DiscountType.PERCENTAGE, new BigDecimal("10.0"), "Loyalty Member"));

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

        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(1);
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO order = invocation.getArgument(0);
            order.setOrderId(101L);
            return order;
        });

        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            OrderResponse resp = new OrderResponse();
            resp.setOrderId(dao.getOrderId());
            resp.setSubTotal(dao.getSubTotal());
            resp.setTaxAmount(dao.getTaxAmount());
            resp.setServiceChargeAmount(dao.getServiceChargeAmount());
            resp.setDiscountAmount(dao.getDiscountAmount());
            resp.setOrderDiscountType(dao.getOrderDiscountType());
            resp.setOrderDiscountRate(dao.getOrderDiscountRate());
            resp.setOrderDiscountAmount(dao.getOrderDiscountAmount());
            resp.setOrderDiscountReason(dao.getOrderDiscountReason());
            resp.setTotalAmount(dao.getTotalAmount());
            resp.setTaxesAndCharges(dao.getTaxesAndCharges());
            return resp;
        });

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        // subTotal = 100.00, Tax = 5.00, Service Charge = 10.00
        // Order discount 10% = 10.00
        // totalPayable = 100.00 + 5.00 + 10.00 - 10.00 = 105.00
        assertEquals(new BigDecimal("100.00"), response.getSubTotal());
        assertEquals(new BigDecimal("5.00"), response.getTaxAmount());
        assertEquals(new BigDecimal("10.00"), response.getServiceChargeAmount());
        assertEquals(new BigDecimal("10.00"), response.getDiscountAmount());
        assertEquals(DiscountType.PERCENTAGE, response.getOrderDiscountType());
        assertEquals(new BigDecimal("10.0"), response.getOrderDiscountRate());
        assertEquals(new BigDecimal("10.00"), response.getOrderDiscountAmount());
        assertEquals("Loyalty Member", response.getOrderDiscountReason());
        assertEquals(new BigDecimal("105.00"), response.getTotalAmount());

        // 3 restaurant charges + 1 order discount = 4 entries
        assertEquals(4, response.getTaxesAndCharges().size());
        assertEquals("Loyalty Member", response.getTaxesAndCharges().get(3).getName());
        assertEquals("ORDER_DISCOUNT", response.getTaxesAndCharges().get(3).getCategory());
        assertEquals(new BigDecimal("10.00"), response.getTaxesAndCharges().get(3).getCalculatedAmount());
    }

    @Test
    public void testCreateOrderWithFixedOrderDiscount() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setEntityNo("10");
        request.setPaymentMode(PaymentMode.CASH);
        request.setCustomerName("Jane Doe");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(2);
        request.setItems(Collections.singletonList(itemRequest));

        // Fixed $25 order discount
        request.setDiscount(new OrderDiscountRequest(DiscountType.FIXED, new BigDecimal("25.00"), "Coupon25"));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setRestaurantName("Tasty Restaurant");
        restaurant.setStatus("ACTIVE");
        restaurant.setTaxesAndCharges(Collections.emptyList());

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

        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(1);
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            OrderResponse resp = new OrderResponse();
            resp.setSubTotal(dao.getSubTotal());
            resp.setDiscountAmount(dao.getDiscountAmount());
            resp.setOrderDiscountType(dao.getOrderDiscountType());
            resp.setOrderDiscountAmount(dao.getOrderDiscountAmount());
            resp.setOrderDiscountReason(dao.getOrderDiscountReason());
            resp.setTotalAmount(dao.getTotalAmount());
            resp.setTaxesAndCharges(dao.getTaxesAndCharges());
            return resp;
        });

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        // subTotal = 100.00, discount = 25.00, total = 75.00
        assertEquals(new BigDecimal("100.00"), response.getSubTotal());
        assertEquals(new BigDecimal("25.00"), response.getDiscountAmount());
        assertEquals(DiscountType.FIXED, response.getOrderDiscountType());
        assertEquals(new BigDecimal("25.00"), response.getOrderDiscountAmount());
        assertEquals(new BigDecimal("75.00"), response.getTotalAmount());
    }

    @Test
    public void testCreateOrderWithCombinedRestaurantAndOrderDiscount() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setEntityNo("10");
        request.setPaymentMode(PaymentMode.CASH);
        request.setCustomerName("Jane Doe");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setMenuId(101L);
        itemRequest.setQuantity(2);
        request.setItems(Collections.singletonList(itemRequest));

        // Order discount: 10%
        request.setDiscount(
                new OrderDiscountRequest(DiscountType.PERCENTAGE, new BigDecimal("10.0"), "Manager Discount"));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setRestaurantName("Tasty Restaurant");
        restaurant.setStatus("ACTIVE");

        // Restaurant-level discount: Fixed $15.00
        List<RestaurantChargeDto> charges = new ArrayList<>();
        charges.add(new RestaurantChargeDto("Restaurant Promo", "FIXED", new BigDecimal("15.00"), "DISCOUNT"));
        restaurant.setTaxesAndCharges(charges);

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

        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenCounterRepository.getNextTokenNo(eq(1L), any(LocalDate.class))).thenReturn(1);
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            OrderResponse resp = new OrderResponse();
            resp.setSubTotal(dao.getSubTotal());
            resp.setDiscountAmount(dao.getDiscountAmount());
            resp.setOrderDiscountAmount(dao.getOrderDiscountAmount());
            resp.setTotalAmount(dao.getTotalAmount());
            resp.setTaxesAndCharges(dao.getTaxesAndCharges());
            return resp;
        });

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        // subTotal = 100.00
        // Restaurant discount = 15.00, Order discount = 10.00 => Total discount = 25.00
        // totalPayable = 100.00 - 25.00 = 75.00
        assertEquals(new BigDecimal("100.00"), response.getSubTotal());
        assertEquals(new BigDecimal("25.00"), response.getDiscountAmount());
        assertEquals(new BigDecimal("10.00"), response.getOrderDiscountAmount());
        assertEquals(new BigDecimal("75.00"), response.getTotalAmount());
        assertEquals(2, response.getTaxesAndCharges().size());
    }

    @Test
    public void testApplyOrderDiscountOnExistingActiveOrder() {
        // Arrange
        OrderDAO order = new OrderDAO();
        order.setOrderId(10L);
        order.setRestaurantId(1L);
        order.setStatus(OrderStatus.PREPARING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMode(PaymentMode.CASH);

        OrderItemDAO item = new OrderItemDAO();
        item.setItemName("Burger");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setTotalItemPrice(new BigDecimal("100.00"));
        item.setOrder(order);
        order.setItems(new ArrayList<>(List.of(item)));

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setTaxesAndCharges(Collections.emptyList());
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            OrderResponse resp = new OrderResponse();
            resp.setOrderId(dao.getOrderId());
            resp.setSubTotal(dao.getSubTotal());
            resp.setDiscountAmount(dao.getDiscountAmount());
            resp.setOrderDiscountType(dao.getOrderDiscountType());
            resp.setOrderDiscountRate(dao.getOrderDiscountRate());
            resp.setOrderDiscountAmount(dao.getOrderDiscountAmount());
            resp.setOrderDiscountReason(dao.getOrderDiscountReason());
            resp.setTotalAmount(dao.getTotalAmount());
            return resp;
        });

        // Act
        OrderDiscountRequest discountReq = new OrderDiscountRequest(DiscountType.PERCENTAGE, new BigDecimal("20.0"),
                "Staff Promo");
        OrderResponse response = orderService.applyOrderDiscount(10L, discountReq);

        // Assert
        assertEquals(new BigDecimal("100.00"), response.getSubTotal());
        assertEquals(new BigDecimal("20.00"), response.getDiscountAmount());
        assertEquals(DiscountType.PERCENTAGE, response.getOrderDiscountType());
        assertEquals(new BigDecimal("20.0"), response.getOrderDiscountRate());
        assertEquals(new BigDecimal("20.00"), response.getOrderDiscountAmount());
        assertEquals("Staff Promo", response.getOrderDiscountReason());
        assertEquals(new BigDecimal("80.00"), response.getTotalAmount());

        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testRemoveOrderDiscount() {
        // Arrange
        OrderDAO order = new OrderDAO();
        order.setOrderId(10L);
        order.setRestaurantId(1L);
        order.setStatus(OrderStatus.PREPARING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMode(PaymentMode.CASH);
        order.setOrderDiscountType(DiscountType.FIXED);
        order.setOrderDiscountRate(new BigDecimal("20.00"));
        order.setOrderDiscountAmount(new BigDecimal("20.00"));
        order.setOrderDiscountReason("Old Discount");

        OrderItemDAO item = new OrderItemDAO();
        item.setItemName("Burger");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setTotalItemPrice(new BigDecimal("100.00"));
        item.setOrder(order);
        order.setItems(new ArrayList<>(List.of(item)));

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setTaxesAndCharges(Collections.emptyList());
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            OrderResponse resp = new OrderResponse();
            resp.setOrderId(dao.getOrderId());
            resp.setSubTotal(dao.getSubTotal());
            resp.setDiscountAmount(dao.getDiscountAmount());
            resp.setOrderDiscountAmount(dao.getOrderDiscountAmount());
            resp.setTotalAmount(dao.getTotalAmount());
            return resp;
        });

        // Act
        OrderResponse response = orderService.removeOrderDiscount(10L);

        // Assert
        assertEquals(new BigDecimal("100.00"), response.getSubTotal());
        assertEquals(BigDecimal.ZERO, response.getDiscountAmount());
        assertEquals(BigDecimal.ZERO, response.getOrderDiscountAmount());
        assertEquals(new BigDecimal("100.00"), response.getTotalAmount());

        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testApplyOrderDiscountOnCancelledOrderFails() {
        OrderDAO order = new OrderDAO();
        order.setOrderId(10L);
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.COMPLETED);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        OrderDiscountRequest discountReq = new OrderDiscountRequest(DiscountType.PERCENTAGE, new BigDecimal("10.0"),
                "Promo");
        assertThrows(IllegalArgumentException.class, () -> orderService.applyOrderDiscount(10L, discountReq));
    }

    @Test
    public void testUpdateOrderItemsAndRecalculatesTotals() {
        // Arrange
        OrderDAO existingOrder = new OrderDAO();
        existingOrder.setOrderId(200L);
        existingOrder.setRestaurantId(1L);
        existingOrder.setStatus(OrderStatus.PENDING);
        existingOrder.setPaymentStatus(PaymentStatus.PENDING);
        existingOrder.setPaymentMode(PaymentMode.CASH);
        existingOrder.setOrderType(OrderType.DINE_IN);
        existingOrder.setEntityNo("Table-1");

        CustomerDAO customer = new CustomerDAO();
        customer.setCustomerName("Alice");
        customer.setPhone("1234567890");
        existingOrder.setCustomer(customer);

        OrderItemDAO oldItem = new OrderItemDAO();
        oldItem.setMenuId(101L);
        oldItem.setQuantity(1);
        oldItem.setUnitPrice(new BigDecimal("100.00"));
        oldItem.setTotalItemPrice(new BigDecimal("100.00"));
        oldItem.setOrder(existingOrder);
        existingOrder.getItems().add(oldItem);

        when(orderRepository.findById(200L)).thenReturn(Optional.of(existingOrder));

        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setRestaurantId(1L);
        restaurant.setRestaurantName("Tasty Restaurant");
        restaurant.setStatus("ACTIVE");
        List<RestaurantChargeDto> charges = new ArrayList<>();
        charges.add(new RestaurantChargeDto("GST", "PERCENTAGE", new BigDecimal("5.0"), "TAX"));
        restaurant.setTaxesAndCharges(charges);
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        // Mock new menu item
        RestaurantValidationService.MenuResponse menuResponse = new RestaurantValidationService.MenuResponse();
        menuResponse.setMenuId(102L);
        menuResponse.setPrice(new BigDecimal("150.00"));
        menuResponse.setItemName("Paneer Butter Masala");
        when(validationService.validateMenuAndGetPrice(102L)).thenReturn(menuResponse);

        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(inv -> {
            OrderDAO dao = inv.getArgument(0);
            OrderResponse resp = new OrderResponse();
            resp.setOrderId(dao.getOrderId());
            resp.setSubTotal(dao.getSubTotal());
            resp.setTaxAmount(dao.getTaxAmount());
            resp.setTotalAmount(dao.getTotalAmount());
            return resp;
        });

        // Act - update items to 2x 102L (2 * 150 = 300)
        UpdateOrderRequest updateReq = new UpdateOrderRequest();
        OrderItemRequest newItem = new OrderItemRequest();
        newItem.setMenuId(102L);
        newItem.setQuantity(2);
        updateReq.setItems(List.of(newItem));

        OrderResponse response = orderService.updateOrder(200L, updateReq);

        // Assert: subtotal = 300, 5% tax = 15, total = 315
        assertNotNull(response);
        assertEquals(new BigDecimal("300.00"), response.getSubTotal());
        assertEquals(new BigDecimal("15.00"), response.getTaxAmount());
        assertEquals(new BigDecimal("315.00"), response.getTotalAmount());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    public void testUpdateOrderCustomerAndNotes() {
        // Arrange
        OrderDAO existingOrder = new OrderDAO();
        existingOrder.setOrderId(201L);
        existingOrder.setRestaurantId(1L);
        existingOrder.setStatus(OrderStatus.PENDING);
        existingOrder.setPaymentStatus(PaymentStatus.PENDING);
        existingOrder.setPaymentMode(PaymentMode.CASH);
        existingOrder.setOrderType(OrderType.DINE_IN);
        existingOrder.setEntityNo("Table-1");

        CustomerDAO customer = new CustomerDAO();
        customer.setCustomerName("Alice");
        customer.setPhone("1234567890");
        existingOrder.setCustomer(customer);

        when(orderRepository.findById(201L)).thenReturn(Optional.of(existingOrder));
        when(validationService.validateRestaurant(1L)).thenReturn(new RestaurantValidationService.RestaurantResponse());
        when(customerRepository.save(any(CustomerDAO.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenReturn(new OrderResponse());

        // Act
        UpdateOrderRequest updateReq = new UpdateOrderRequest();
        updateReq.setCustomerName("Bob");
        updateReq.setPhone("9999999999");
        updateReq.setNotes("Extra spicy");

        orderService.updateOrder(201L, updateReq);

        // Assert
        assertEquals("Bob", existingOrder.getCustomer().getCustomerName());
        assertEquals("9999999999", existingOrder.getCustomer().getPhone());
        assertEquals("Extra spicy", existingOrder.getNotes());
        verify(customerRepository, times(1)).save(any(CustomerDAO.class));
    }

    @Test
    public void testUpdateOrderEntityAndTableOccupancy() {
        // Arrange
        OrderDAO existingOrder = new OrderDAO();
        existingOrder.setOrderId(202L);
        existingOrder.setRestaurantId(1L);
        existingOrder.setStatus(OrderStatus.PENDING);
        existingOrder.setPaymentStatus(PaymentStatus.PENDING);
        existingOrder.setPaymentMode(PaymentMode.CASH);
        existingOrder.setOrderType(OrderType.DINE_IN);
        existingOrder.setEntityNo("Table-1");

        when(orderRepository.findById(202L)).thenReturn(Optional.of(existingOrder));
        when(validationService.validateRestaurant(1L)).thenReturn(new RestaurantValidationService.RestaurantResponse());
        RestaurantValidationService.EntityResponse entityResp = new RestaurantValidationService.EntityResponse();
        entityResp.setEntityNo("Table-2");
        entityResp.setOrderEntityType("TABLE");
        when(validationService.validateEntity("Table-2", 1L)).thenReturn(entityResp);
        when(orderRepository.existsByRestaurantIdAndEntityNoAndStatusInAndOrderIdNot(eq(1L), eq("Table-1"), anyList(),
                eq(202L))).thenReturn(false);
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenReturn(new OrderResponse());

        // Act - switch table to Table-2
        UpdateOrderRequest updateReq = new UpdateOrderRequest();
        updateReq.setEntityNo("Table-2");

        orderService.updateOrder(202L, updateReq);

        // Assert
        assertEquals("Table-2", existingOrder.getEntityNo());
        assertEquals("TABLE", existingOrder.getOrderEntityType());
        verify(validationService, times(1)).updateEntityStatus("Table-2", 1L,
                OrderEntityStatus.OCCUPIED);
        verify(validationService, times(1)).updateEntityStatus("Table-1", 1L,
                OrderEntityStatus.AVAILABLE);
    }

    @Test
    public void testUpdateOrderOnCancelledOrderFails() {
        UpdateOrderRequest updateReq = new UpdateOrderRequest();
        updateReq.setNotes("Should fail");

        OrderDAO cancelledOrder = new OrderDAO();
        cancelledOrder.setOrderId(204L);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(204L)).thenReturn(Optional.of(cancelledOrder));

        assertThrows(IllegalArgumentException.class, () -> orderService.updateOrder(204L, updateReq));
    }

    @Test
    public void testUpdateOrderItemsOnCompletedPaymentSuccess() {
        OrderDAO paidOrder = new OrderDAO();
        paidOrder.setOrderId(205L);
        paidOrder.setRestaurantId(1L);
        paidOrder.setStatus(OrderStatus.PREPARING);
        paidOrder.setPaymentStatus(PaymentStatus.COMPLETED);
        when(orderRepository.findById(205L)).thenReturn(Optional.of(paidOrder));
        when(validationService.validateRestaurant(1L)).thenReturn(new RestaurantValidationService.RestaurantResponse());

        RestaurantValidationService.MenuResponse menuResponse = new RestaurantValidationService.MenuResponse();
        menuResponse.setMenuId(101L);
        menuResponse.setPrice(new BigDecimal("100.00"));
        menuResponse.setItemName("Paneer");
        when(validationService.validateMenuAndGetPrice(101L)).thenReturn(menuResponse);
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenReturn(new OrderResponse());

        UpdateOrderRequest updateReq = new UpdateOrderRequest();
        OrderItemRequest item = new OrderItemRequest();
        item.setMenuId(101L);
        item.setQuantity(2);
        updateReq.setItems(List.of(item));

        OrderResponse response = orderService.updateOrder(205L, updateReq);
        assertNotNull(response);
    }

    @Test
    public void testUpdateOrderWithSubPaymentModeSuccess() {
        OrderDAO cashOrder = new OrderDAO();
        cashOrder.setOrderId(300L);
        cashOrder.setRestaurantId(1L);
        cashOrder.setOrderType(OrderType.DINE_IN);
        cashOrder.setEntityNo("Table-1");
        cashOrder.setStatus(OrderStatus.PREPARING);
        cashOrder.setPaymentMode(PaymentMode.CASH);
        cashOrder.setPaymentStatus(PaymentStatus.PENDING);

        when(orderRepository.findById(300L)).thenReturn(Optional.of(cashOrder));
        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setPaymentModes(List.of(PaymentMode.CASH, PaymentMode.ONLINE));
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            OrderResponse response = new OrderResponse();
            response.setOrderId(dao.getOrderId());
            response.setPaymentMode(dao.getPaymentMode());
            response.setSubPaymentMode(dao.getSubPaymentMode());
            return response;
        });

        UpdateOrderRequest updateReq = new UpdateOrderRequest();
        updateReq.setSubPaymentMode(SubPaymentMode.UPI);

        OrderResponse response = orderService.updateOrder(300L, updateReq);

        assertEquals(PaymentMode.CASH, response.getPaymentMode());
        assertEquals(SubPaymentMode.UPI, response.getSubPaymentMode());
        assertEquals(SubPaymentMode.UPI, cashOrder.getSubPaymentMode());
    }

    @Test
    public void testUpdateOrderWithSubPaymentModeFailsForOnlineOrder() {
        OrderDAO onlineOrder = new OrderDAO();
        onlineOrder.setOrderId(301L);
        onlineOrder.setRestaurantId(1L);
        onlineOrder.setStatus(OrderStatus.PREPARING);
        onlineOrder.setPaymentMode(PaymentMode.ONLINE);
        onlineOrder.setPaymentStatus(PaymentStatus.PENDING);

        when(orderRepository.findById(301L)).thenReturn(Optional.of(onlineOrder));
        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setPaymentModes(List.of(PaymentMode.CASH, PaymentMode.ONLINE));
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);

        UpdateOrderRequest updateReq = new UpdateOrderRequest();
        updateReq.setSubPaymentMode(SubPaymentMode.CARD);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.updateOrder(301L, updateReq));
        assertEquals("subPaymentMode is only allowed when paymentMode is CASH", ex.getMessage());
    }

    @Test
    public void testUpdateOrderSwitchingToOnlineClearsSubPaymentMode() {
        OrderDAO cashOrder = new OrderDAO();
        cashOrder.setOrderId(302L);
        cashOrder.setRestaurantId(1L);
        cashOrder.setStatus(OrderStatus.PENDING);
        cashOrder.setPaymentMode(PaymentMode.CASH);
        cashOrder.setSubPaymentMode(SubPaymentMode.CASH);
        cashOrder.setPaymentStatus(PaymentStatus.PENDING);

        when(orderRepository.findById(302L)).thenReturn(Optional.of(cashOrder));
        RestaurantValidationService.RestaurantResponse restaurant = new RestaurantValidationService.RestaurantResponse();
        restaurant.setPaymentModes(List.of(PaymentMode.CASH, PaymentMode.ONLINE));
        when(validationService.validateRestaurant(1L)).thenReturn(restaurant);
        when(orderRepository.save(any(OrderDAO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.orderDAOToOrderResponse(any(OrderDAO.class))).thenAnswer(invocation -> {
            OrderDAO dao = invocation.getArgument(0);
            OrderResponse response = new OrderResponse();
            response.setOrderId(dao.getOrderId());
            response.setPaymentMode(dao.getPaymentMode());
            response.setSubPaymentMode(dao.getSubPaymentMode());
            return response;
        });

        UpdateOrderRequest updateReq = new UpdateOrderRequest();
        updateReq.setPaymentMode(PaymentMode.ONLINE);

        OrderResponse response = orderService.updateOrder(302L, updateReq);

        assertEquals(PaymentMode.ONLINE, response.getPaymentMode());
        assertNull(response.getSubPaymentMode());
        assertNull(cashOrder.getSubPaymentMode());
    }
}
