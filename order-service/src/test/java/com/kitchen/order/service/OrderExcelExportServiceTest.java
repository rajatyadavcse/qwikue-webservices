package com.kitchen.order.service;

import com.kitchen.order.dao.CustomerDAO;
import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.dao.OrderItemDAO;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.enums.OrderType;
import com.kitchen.order.enums.PaymentMode;
import com.kitchen.order.enums.PaymentStatus;
import com.kitchen.order.repository.OrderRepository;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExcelExportServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderExcelExportService orderExcelExportService;

    private OrderDAO sampleOrder;

    @BeforeEach
    void setUp() {
        CustomerDAO customer = new CustomerDAO();
        customer.setCustomerId(101L);
        customer.setCustomerName("Rajat Yadav");
        customer.setPhone("9876543210");

        OrderItemDAO item1 = new OrderItemDAO();
        item1.setOrderItemId(1L);
        item1.setMenuId(501L);
        item1.setItemName("Paneer Tikka");
        item1.setQuantity(2);
        item1.setUnitPrice(new BigDecimal("250.00"));
        item1.setTotalItemPrice(new BigDecimal("500.00"));

        OrderItemDAO item2 = new OrderItemDAO();
        item2.setOrderItemId(2L);
        item2.setMenuId(502L);
        item2.setItemName("Butter Naan");
        item2.setQuantity(3);
        item2.setUnitPrice(new BigDecimal("40.00"));
        item2.setTotalItemPrice(new BigDecimal("120.00"));

        sampleOrder = new OrderDAO();
        sampleOrder.setOrderId(1001L);
        sampleOrder.setRestaurantId(10L);
        sampleOrder.setTokenNo(7);
        sampleOrder.setEntityNo("T-12");
        sampleOrder.setOrderEntityType("Table");
        sampleOrder.setOrderType(OrderType.DINE_IN);
        sampleOrder.setStatus(OrderStatus.COMPLETED);
        sampleOrder.setPaymentMode(PaymentMode.CASH);
        sampleOrder.setPaymentStatus(PaymentStatus.COMPLETED);
        sampleOrder.setSubTotal(new BigDecimal("620.00"));
        sampleOrder.setTaxAmount(new BigDecimal("31.00"));
        sampleOrder.setServiceChargeAmount(BigDecimal.ZERO);
        sampleOrder.setDiscountAmount(BigDecimal.ZERO);
        sampleOrder.setTipAmount(BigDecimal.ZERO);
        sampleOrder.setTotalAmount(new BigDecimal("651.00"));
        sampleOrder.setCreatedAt(LocalDateTime.of(2026, 9, 15, 13, 30));
        sampleOrder.setCompletedAt(LocalDateTime.of(2026, 9, 15, 14, 15));
        sampleOrder.setCustomer(customer);

        List<OrderItemDAO> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        sampleOrder.setItems(items);
        item1.setOrder(sampleOrder);
        item2.setOrder(sampleOrder);
    }

    @Test
    void testExportOrdersToExcel_WithOrders() throws IOException {
        Long restaurantId = 10L;
        OrderStatus status = OrderStatus.COMPLETED;
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 30);

        when(orderRepository.findDistinctByRestaurantIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(restaurantId), eq(status), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(sampleOrder));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        orderExcelExportService.exportOrdersToExcel(restaurantId, status, fromDate, toDate, out);

        byte[] bytes = out.toByteArray();
        assertTrue(bytes.length > 0, "Generated Excel file should not be empty");

        // Verify Excel Workbook Structure
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets(), "Workbook should contain 2 sheets");

            // Verify Sheet 1: Orders
            XSSFSheet ordersSheet = workbook.getSheet("Orders");
            assertNotNull(ordersSheet, "Orders sheet should exist");
            assertEquals(2, ordersSheet.getPhysicalNumberOfRows(), "Orders sheet should have 1 header and 1 data row");

            XSSFRow orderDataRow = ordersSheet.getRow(1);
            assertEquals(1001.0, orderDataRow.getCell(0).getNumericCellValue(), "Order ID should match");
            assertEquals(7.0, orderDataRow.getCell(1).getNumericCellValue(), "Token No should match");
            assertEquals("DINE_IN", orderDataRow.getCell(3).getStringCellValue(), "Order Type should match");
            assertEquals("Table T-12", orderDataRow.getCell(4).getStringCellValue(), "Entity should match");
            assertEquals("COMPLETED", orderDataRow.getCell(5).getStringCellValue(), "Status should match");
            assertEquals("Rajat Yadav", orderDataRow.getCell(7).getStringCellValue(), "Customer name should match");
            assertEquals("9876543210", orderDataRow.getCell(8).getStringCellValue(), "Customer phone should match");
            assertTrue(orderDataRow.getCell(9).getStringCellValue().contains("Paneer Tikka"), "Items summary should contain item name");
            assertEquals(651.0, orderDataRow.getCell(15).getNumericCellValue(), "Total amount should match");

            // Verify Sheet 2: Order Items
            XSSFSheet itemsSheet = workbook.getSheet("Order Items");
            assertNotNull(itemsSheet, "Order Items sheet should exist");
            assertEquals(3, itemsSheet.getPhysicalNumberOfRows(), "Order items sheet should have 1 header and 2 item rows");

            XSSFRow item1Row = itemsSheet.getRow(1);
            assertEquals(1001.0, item1Row.getCell(0).getNumericCellValue(), "Order ID should match");
            assertEquals("Paneer Tikka", item1Row.getCell(5).getStringCellValue(), "Item name should match");
            assertEquals(2.0, item1Row.getCell(6).getNumericCellValue(), "Quantity should match");
            assertEquals(250.0, item1Row.getCell(7).getNumericCellValue(), "Unit price should match");
            assertEquals(500.0, item1Row.getCell(8).getNumericCellValue(), "Total item price should match");

            XSSFRow item2Row = itemsSheet.getRow(2);
            assertEquals("Butter Naan", item2Row.getCell(5).getStringCellValue(), "Second item name should match");
            assertEquals(3.0, item2Row.getCell(6).getNumericCellValue(), "Quantity should match");
        }
    }

    @Test
    void testExportOrdersToExcel_PaymentPendingStatusReturnsEmptyWorkbook() throws IOException {
        Long restaurantId = 10L;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        orderExcelExportService.exportOrdersToExcel(restaurantId, OrderStatus.PAYMENT_PENDING, null, null, out);

        // Verify repository is never queried for PAYMENT_PENDING
        verifyNoInteractions(orderRepository);

        byte[] bytes = out.toByteArray();
        assertTrue(bytes.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet ordersSheet = workbook.getSheet("Orders");
            assertEquals(1, ordersSheet.getPhysicalNumberOfRows(), "Only header row should be present");
        }
    }

    @Test
    void testExportOrdersToExcel_NoDates_WithStatus() throws IOException {
        Long restaurantId = 10L;
        OrderStatus status = OrderStatus.PREPARING;

        when(orderRepository.findDistinctByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, status))
                .thenReturn(List.of(sampleOrder));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        orderExcelExportService.exportOrdersToExcel(restaurantId, status, null, null, out);

        verify(orderRepository).findDistinctByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, status);
    }

    @Test
    void testExportOrdersToExcel_NoDates_NoStatus() throws IOException {
        Long restaurantId = 10L;

        when(orderRepository.findDistinctByRestaurantIdAndStatusNotOrderByCreatedAtDesc(
                restaurantId, OrderStatus.PAYMENT_PENDING))
                .thenReturn(List.of(sampleOrder));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        orderExcelExportService.exportOrdersToExcel(restaurantId, null, null, null, out);

        verify(orderRepository).findDistinctByRestaurantIdAndStatusNotOrderByCreatedAtDesc(
                restaurantId, OrderStatus.PAYMENT_PENDING);
    }
}
