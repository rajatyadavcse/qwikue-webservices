package com.kitchen.order.scheduler;

import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.enums.PaymentStatus;
import com.kitchen.order.repository.OrderRepository;
import com.kitchen.order.service.IOrderService;
import com.kitchen.order.service.RestaurantValidationService;
import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentSweeperSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private IOrderService orderService;

    @Mock
    private RestaurantValidationService validationService;

    @InjectMocks
    private PaymentSweeperScheduler paymentSweeperScheduler;

    private RestaurantValidationService.RestaurantResponse mockRestaurant;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentSweeperScheduler, "staleMinutes", 15);

        mockRestaurant = new RestaurantValidationService.RestaurantResponse();
        mockRestaurant.setRestaurantId(1L);
        mockRestaurant.setRazorpayKeyId("rzp_test_key");
        mockRestaurant.setRazorpayKeySecret("rzp_test_secret");
    }

    @Test
    public void testSweepStalePaymentsNoStaleOrders() {
        when(orderRepository.findByPaymentModeAndPaymentStatusAndCreatedAtBefore(
                eq(PaymentMode.ONLINE), eq(PaymentStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        paymentSweeperScheduler.sweepStalePayments();

        verifyNoInteractions(validationService, orderService);
    }

    @Test
    public void testSweepStalePaymentsFailsDirectlyWhenNoRazorpayOrderId() {
        OrderDAO order = new OrderDAO();
        order.setOrderId(101L);
        order.setRestaurantId(1L);
        order.setPaymentMode(PaymentMode.ONLINE);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setRazorpayOrderId(null);

        when(orderRepository.findByPaymentModeAndPaymentStatusAndCreatedAtBefore(
                eq(PaymentMode.ONLINE), eq(PaymentStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(order));

        paymentSweeperScheduler.sweepStalePayments();

        verify(orderService).failPayment(101L, "No Razorpay Order ID created");
        verifyNoInteractions(validationService);
    }

    @Test
    public void testSweepStalePaymentsAutoCompletesWhenPaid() throws Exception {
        OrderDAO order = new OrderDAO();
        order.setOrderId(102L);
        order.setRestaurantId(1L);
        order.setPaymentMode(PaymentMode.ONLINE);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setRazorpayOrderId("rzp_order_abc");

        when(orderRepository.findByPaymentModeAndPaymentStatusAndCreatedAtBefore(
                eq(PaymentMode.ONLINE), eq(PaymentStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(order));

        when(validationService.validateRestaurant(1L)).thenReturn(mockRestaurant);

        // Set up Mock Razorpay SDK classes
        Order mockRazorpayOrder = mock(Order.class);
        when(mockRazorpayOrder.get("status")).thenReturn("paid");

        Payment mockPayment = mock(Payment.class);
        when(mockPayment.get("status")).thenReturn("captured");
        when(mockPayment.get("id")).thenReturn("pay_captured_123");

        OrderClient mockOrderClient = mock(OrderClient.class);
        when(mockOrderClient.fetch("rzp_order_abc")).thenReturn(mockRazorpayOrder);
        when(mockOrderClient.fetchPayments("rzp_order_abc")).thenReturn(Collections.singletonList(mockPayment));

        try (MockedConstruction<RazorpayClient> ignored = mockConstruction(RazorpayClient.class, (mock, context) -> {
            ReflectionTestUtils.setField(mock, "orders", mockOrderClient);
        })) {
            paymentSweeperScheduler.sweepStalePayments();
        }

        verify(orderService).completePayment(102L, "pay_captured_123");
    }

    @Test
    public void testSweepStalePaymentsFailsWhenUnpaid() throws Exception {
        OrderDAO order = new OrderDAO();
        order.setOrderId(103L);
        order.setRestaurantId(1L);
        order.setPaymentMode(PaymentMode.ONLINE);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setRazorpayOrderId("rzp_order_def");

        when(orderRepository.findByPaymentModeAndPaymentStatusAndCreatedAtBefore(
                eq(PaymentMode.ONLINE), eq(PaymentStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(order));

        when(validationService.validateRestaurant(1L)).thenReturn(mockRestaurant);

        Order mockRazorpayOrder = mock(Order.class);
        when(mockRazorpayOrder.get("status")).thenReturn("created");

        OrderClient mockOrderClient = mock(OrderClient.class);
        when(mockOrderClient.fetch("rzp_order_def")).thenReturn(mockRazorpayOrder);

        try (MockedConstruction<RazorpayClient> ignored = mockConstruction(RazorpayClient.class, (mock, context) -> {
            ReflectionTestUtils.setField(mock, "orders", mockOrderClient);
        })) {
            paymentSweeperScheduler.sweepStalePayments();
        }

        verify(orderService).failPayment(103L, "Payment verification window expired. Razorpay status: created");
    }

    @Test
    public void testSweepStalePaymentsFailsWhenOrderNotFoundOnRazorpay() throws Exception {
        OrderDAO order = new OrderDAO();
        order.setOrderId(104L);
        order.setRestaurantId(1L);
        order.setPaymentMode(PaymentMode.ONLINE);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setRazorpayOrderId("rzp_order_invalid");

        when(orderRepository.findByPaymentModeAndPaymentStatusAndCreatedAtBefore(
                eq(PaymentMode.ONLINE), eq(PaymentStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(order));

        when(validationService.validateRestaurant(1L)).thenReturn(mockRestaurant);

        OrderClient mockOrderClient = mock(OrderClient.class);
        // Throw bad request RazorpayException
        String jsonError = "{\"error\": {\"code\": \"BAD_REQUEST_ERROR\", \"description\": \"The order id rzp_order_invalid does not exist\"}}";
        when(mockOrderClient.fetch("rzp_order_invalid")).thenThrow(new RazorpayException(jsonError));

        try (MockedConstruction<RazorpayClient> ignored = mockConstruction(RazorpayClient.class, (mock, context) -> {
            ReflectionTestUtils.setField(mock, "orders", mockOrderClient);
        })) {
            paymentSweeperScheduler.sweepStalePayments();
        }

        verify(orderService).failPayment(eq(104L), contains("Razorpay order invalid"));
    }

    @Test
    public void testSweepStalePaymentsContinuesOnExceptions() throws Exception {
        OrderDAO order1 = new OrderDAO();
        order1.setOrderId(105L);
        order1.setRestaurantId(1L);
        order1.setPaymentMode(PaymentMode.ONLINE);
        order1.setPaymentStatus(PaymentStatus.PENDING);
        order1.setRazorpayOrderId("rzp_order_erroneous");

        OrderDAO order2 = new OrderDAO();
        order2.setOrderId(106L);
        order2.setRestaurantId(1L);
        order2.setPaymentMode(PaymentMode.ONLINE);
        order2.setPaymentStatus(PaymentStatus.PENDING);
        order2.setRazorpayOrderId("rzp_order_fine");

        when(orderRepository.findByPaymentModeAndPaymentStatusAndCreatedAtBefore(
                eq(PaymentMode.ONLINE), eq(PaymentStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(order1, order2));

        when(validationService.validateRestaurant(1L)).thenReturn(mockRestaurant);

        OrderClient mockOrderClient = mock(OrderClient.class);
        // Throw a temporary/server/network error for order1 (not a BAD_REQUEST_ERROR, so it is skipped/logged and doesn't call failPayment)
        when(mockOrderClient.fetch("rzp_order_erroneous")).thenThrow(new RazorpayException("Connection timed out"));

        // Order2 completes normally as paid
        Order mockRazorpayOrder2 = mock(Order.class);
        when(mockRazorpayOrder2.get("status")).thenReturn("paid");
        when(mockOrderClient.fetch("rzp_order_fine")).thenReturn(mockRazorpayOrder2);

        Payment mockPayment = mock(Payment.class);
        when(mockPayment.get("status")).thenReturn("captured");
        when(mockPayment.get("id")).thenReturn("pay_captured_456");
        when(mockOrderClient.fetchPayments("rzp_order_fine")).thenReturn(Collections.singletonList(mockPayment));

        try (MockedConstruction<RazorpayClient> ignored = mockConstruction(RazorpayClient.class, (mock, context) -> {
            ReflectionTestUtils.setField(mock, "orders", mockOrderClient);
        })) {
            assertDoesNotThrow(() -> paymentSweeperScheduler.sweepStalePayments());
        }

        // Verify order1 is NOT updated (neither completePayment nor failPayment is called, since it retries later)
        verify(orderService, never()).completePayment(eq(105L), anyString());
        verify(orderService, never()).failPayment(eq(105L), anyString());

        // Verify order2 is completed successfully
        verify(orderService).completePayment(106L, "pay_captured_456");
    }
}
