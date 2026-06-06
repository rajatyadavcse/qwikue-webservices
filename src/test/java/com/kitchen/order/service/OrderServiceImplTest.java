package com.kitchen.order.service;

import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.dto.request.CreateOrderRequest;
import com.kitchen.order.dto.request.OrderItemRequest;
import com.kitchen.order.dto.response.OrderResponse;
import com.kitchen.order.dto.response.RestaurantChargeDto;
import com.kitchen.order.mapper.OrderMapper;
import com.kitchen.order.repository.OrderRepository;
import com.kitchen.order.repository.RestaurantTokenCounterRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestaurantTokenCounterRepository tokenCounterRepository;

    @Mock
    private RestaurantValidationService validationService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    public void testCreateOrderCalculatesTaxesAndServiceChargesCorrectly() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest();
        request.setRestaurantId(1L);
        request.setEntityNo("10");
        request.setNotes("No onions");

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
            mockResponse.setTotalAmount(dao.getTotalAmount());
            mockResponse.setTaxesAndCharges(dao.getTaxesAndCharges());
            mockResponse.setOrderEntityType(dao.getOrderEntityType());
            mockResponse.setTokenNo(dao.getTokenNo());
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

        verify(eventPublisher, times(1)).publishEvent(any());
    }
}
