package com.kitchen.order.controller;

import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.service.IOrderService;
import com.kitchen.order.service.OrderExcelExportService;
import com.kitchen.order.service.OrderStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrderController.class)
class OrderControllerExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IOrderService orderService;

    @MockBean
    private OrderStreamService streamService;

    @MockBean
    private OrderExcelExportService orderExcelExportService;

    @Test
    void testExportOrdersEndpoint() throws Exception {
        Long restaurantId = 1L;
        OrderStatus status = OrderStatus.COMPLETED;
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 30);

        doAnswer(invocation -> {
            OutputStream os = invocation.getArgument(4);
            os.write("mock-excel-content".getBytes());
            return null;
        }).when(orderExcelExportService).exportOrdersToExcel(
                eq(restaurantId), eq(status), eq(fromDate), eq(toDate), any(OutputStream.class));

        // 1. Initial request starts async streaming
        MvcResult mvcResult = mockMvc.perform(get("/orders/export")
                        .param("restaurantId", restaurantId.toString())
                        .param("status", status.name())
                        .param("fromDate", "2026-09-01")
                        .param("toDate", "2026-09-30"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 2. Complete async execution and verify response
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment; filename=\"orders_1_")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().string("mock-excel-content"));
    }
}
