package com.kitchen.order.service;

import com.kitchen.order.dao.CustomerDAO;
import com.kitchen.order.dao.OrderDAO;
import com.kitchen.order.dao.OrderItemDAO;
import com.kitchen.order.enums.OrderStatus;
import com.kitchen.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for streaming order data to Excel (.xlsx) files.
 * Uses Apache POI's SXSSFWorkbook to maintain a strictly low-memory footprint
 * suitable for containerized environments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExcelExportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int SXSSF_ROW_WINDOW_SIZE = 100;

    private final OrderRepository orderRepository;

    /**
     * Queries matching orders and streams an Excel (.xlsx) workbook directly into the output stream.
     * Logs database fetch time, Excel generation/streaming time, and total API latency.
     */
    @Transactional(readOnly = true)
    public void exportOrdersToExcel(Long restaurantId, OrderStatus status,
                                   LocalDate fromDate, LocalDate toDate,
                                   OutputStream outputStream) throws IOException {

        long overallStartTime = System.currentTimeMillis();

        log.info("[ORDER_EXPORT_START] restaurantId={} | status={} | fromDate={} | toDate={}",
                restaurantId, status, fromDate, toDate);

        // 1. Fetch Orders from DB
        long dbStartTime = System.currentTimeMillis();
        List<OrderDAO> orders = fetchOrders(restaurantId, status, fromDate, toDate);
        long dbDurationMs = System.currentTimeMillis() - dbStartTime;

        int totalOrders = orders.size();
        int totalItems = orders.stream()
                .mapToInt(o -> o.getItems() != null ? o.getItems().size() : 0)
                .sum();

        log.info("[ORDER_EXPORT_DB_FETCH] restaurantId={} | Orders: {} | OrderItems: {} | DB Latency: {} ms",
                restaurantId, totalOrders, totalItems, dbDurationMs);

        // 2. Generate and Stream Excel Workbook
        long excelStartTime = System.currentTimeMillis();
        SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_ROW_WINDOW_SIZE);
        workbook.setCompressTempFiles(true);

        try {
            ExportStyles styles = createExportStyles(workbook);

            // Sheet 1: Orders Summary
            SXSSFSheet ordersSheet = workbook.createSheet("Orders");
            ordersSheet.createFreezePane(0, 1);
            writeOrdersSheet(ordersSheet, orders, styles);

            // Sheet 2: Order Items Breakdown
            SXSSFSheet itemsSheet = workbook.createSheet("Order Items");
            itemsSheet.createFreezePane(0, 1);
            writeOrderItemsSheet(itemsSheet, orders, styles);

            // Flush to response output stream
            workbook.write(outputStream);
            outputStream.flush();

            long excelDurationMs = System.currentTimeMillis() - excelStartTime;
            long totalDurationMs = System.currentTimeMillis() - overallStartTime;

            log.info("[ORDER_EXPORT_SUCCESS] restaurantId={} | Total Orders: {} | Total Items: {} | DB Fetch: {} ms | Excel Gen & Stream: {} ms | Total Latency: {} ms",
                    restaurantId, totalOrders, totalItems, dbDurationMs, excelDurationMs, totalDurationMs);

        } catch (Exception ex) {
            long totalDurationMs = System.currentTimeMillis() - overallStartTime;
            log.error("[ORDER_EXPORT_ERROR] restaurantId={} failed after {} ms (DB: {} ms). Reason: {}",
                    restaurantId, totalDurationMs, dbDurationMs, ex.getMessage(), ex);
            throw ex;
        } finally {
            // Clean up temporary disk files allocated by SXSSFWorkbook
            workbook.dispose();
            workbook.close();
        }
    }

    /**
     * Queries orders matching the exact same filters as the paginated GET /orders endpoint.
     */
    private List<OrderDAO> fetchOrders(Long restaurantId, OrderStatus status, LocalDate fromDate, LocalDate toDate) {
        if (status == OrderStatus.PAYMENT_PENDING) {
            return Collections.emptyList();
        }

        if (fromDate == null && toDate == null) {
            if (status != null) {
                return orderRepository.findDistinctByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, status);
            } else {
                return orderRepository.findDistinctByRestaurantIdAndStatusNotOrderByCreatedAtDesc(
                        restaurantId, OrderStatus.PAYMENT_PENDING);
            }
        }

        LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : null;
        LocalDateTime end = (toDate != null) ? toDate.plusDays(1).atStartOfDay() : null;

        if (start != null && end != null) {
            if (status != null) {
                return orderRepository.findDistinctByRestaurantIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        restaurantId, status, start, end);
            } else {
                return orderRepository.findDistinctByRestaurantIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        restaurantId, OrderStatus.PAYMENT_PENDING, start, end);
            }
        } else if (start != null) {
            if (status != null) {
                return orderRepository.findDistinctByRestaurantIdAndStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        restaurantId, status, start);
            } else {
                return orderRepository.findDistinctByRestaurantIdAndStatusNotAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        restaurantId, OrderStatus.PAYMENT_PENDING, start);
            }
        } else {
            if (status != null) {
                return orderRepository.findDistinctByRestaurantIdAndStatusAndCreatedAtLessThanOrderByCreatedAtDesc(
                        restaurantId, status, end);
            } else {
                return orderRepository.findDistinctByRestaurantIdAndStatusNotAndCreatedAtLessThanOrderByCreatedAtDesc(
                        restaurantId, OrderStatus.PAYMENT_PENDING, end);
            }
        }
    }

    /**
     * Builds Sheet 1 ("Orders"): One row per order with customer, status, totals, and item summaries.
     */
    private void writeOrdersSheet(SXSSFSheet sheet, List<OrderDAO> orders, ExportStyles styles) {
        String[] headers = {
                "Order ID", "Token No", "Date & Time", "Order Type", "Table / Entity",
                "Status", "Ordered By", "Customer Name", "Customer Phone", "Items Summary",
                "Subtotal (\u20B9)", "Discount (\u20B9)", "Tax (\u20B9)", "Service Charge (\u20B9)", "Tip (\u20B9)",
                "Total Amount (\u20B9)", "Payment Mode", "Sub Payment Mode", "Payment Status",
                "Completed At", "Notes", "Cancellation / Delay Reason"
        };

        int[] columnWidths = {
                12, 10, 20, 14, 16,
                14, 14, 20, 16, 36,
                14, 14, 14, 18, 12,
                16, 16, 18, 16,
                20, 24, 28
        };

        // Header Row
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.headerStyle);
            sheet.setColumnWidth(i, columnWidths[i] * 256);
        }

        // Data Rows
        int rowIndex = 1;
        for (OrderDAO order : orders) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(18);

            CustomerDAO customer = order.getCustomer();
            String itemsSummary = (order.getItems() == null || order.getItems().isEmpty())
                    ? ""
                    : order.getItems().stream()
                            .map(item -> String.format("%dx %s", item.getQuantity(), item.getItemName() != null ? item.getItemName() : "Item#" + item.getMenuId()))
                            .collect(Collectors.joining(", "));

            int col = 0;
            createNumberCell(row, col++, order.getOrderId(), styles.centeredStyle);
            createNumberCell(row, col++, order.getTokenNo(), styles.centeredStyle);
            createTextCell(row, col++, formatDateTime(order.getCreatedAt()), styles.centeredStyle);
            createTextCell(row, col++, order.getOrderType() != null ? order.getOrderType().name() : "", styles.centeredStyle);
            createTextCell(row, col++, formatEntity(order), styles.centeredStyle);

            createTextCell(row, col++, order.getStatus() != null ? order.getStatus().name() : "", styles.centeredStyle);
            createTextCell(row, col++, order.getOrderedBy() != null ? order.getOrderedBy().name() : "", styles.centeredStyle);
            createTextCell(row, col++, customer != null ? customer.getCustomerName() : "", styles.textStyle);
            createTextCell(row, col++, customer != null ? customer.getPhone() : "", styles.centeredStyle);
            createTextCell(row, col++, itemsSummary, styles.textStyle);

            createCurrencyCell(row, col++, order.getSubTotal(), styles.currencyStyle);
            createCurrencyCell(row, col++, order.getDiscountAmount(), styles.currencyStyle);
            createCurrencyCell(row, col++, order.getTaxAmount(), styles.currencyStyle);
            createCurrencyCell(row, col++, order.getServiceChargeAmount(), styles.currencyStyle);
            createCurrencyCell(row, col++, order.getTipAmount(), styles.currencyStyle);

            createCurrencyCell(row, col++, order.getTotalAmount(), styles.currencyStyle);
            createTextCell(row, col++, order.getPaymentMode() != null ? order.getPaymentMode().name() : "", styles.centeredStyle);
            createTextCell(row, col++, order.getSubPaymentMode() != null ? order.getSubPaymentMode().name() : "", styles.centeredStyle);
            createTextCell(row, col++, order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "", styles.centeredStyle);

            createTextCell(row, col++, formatDateTime(order.getCompletedAt()), styles.centeredStyle);
            createTextCell(row, col++, order.getNotes(), styles.textStyle);
            createTextCell(row, col++, order.getReason(), styles.textStyle);
        }
    }

    /**
     * Builds Sheet 2 ("Order Items"): Detailed line-by-line item breakdown for kitchen and inventory auditing.
     */
    private void writeOrderItemsSheet(SXSSFSheet sheet, List<OrderDAO> orders, ExportStyles styles) {
        String[] headers = {
                "Order ID", "Token No", "Date & Time", "Table / Entity",
                "Menu ID", "Item Name", "Quantity", "Unit Price (\u20B9)", "Total Price (\u20B9)", "Order Status"
        };

        int[] columnWidths = {
                12, 10, 20, 16,
                12, 30, 10, 16, 16, 16
        };

        // Header Row
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.headerStyle);
            sheet.setColumnWidth(i, columnWidths[i] * 256);
        }

        // Data Rows
        int rowIndex = 1;
        for (OrderDAO order : orders) {
            if (order.getItems() == null || order.getItems().isEmpty()) {
                continue;
            }

            for (OrderItemDAO item : order.getItems()) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(18);

                int col = 0;
                createNumberCell(row, col++, order.getOrderId(), styles.centeredStyle);
                createNumberCell(row, col++, order.getTokenNo(), styles.centeredStyle);
                createTextCell(row, col++, formatDateTime(order.getCreatedAt()), styles.centeredStyle);
                createTextCell(row, col++, formatEntity(order), styles.centeredStyle);

                createNumberCell(row, col++, item.getMenuId(), styles.centeredStyle);
                createTextCell(row, col++, item.getItemName() != null ? item.getItemName() : "Item #" + item.getMenuId(), styles.textStyle);
                createNumberCell(row, col++, item.getQuantity(), styles.centeredStyle);
                createCurrencyCell(row, col++, item.getUnitPrice(), styles.currencyStyle);
                createCurrencyCell(row, col++, item.getTotalItemPrice(), styles.currencyStyle);
                createTextCell(row, col++, order.getStatus() != null ? order.getStatus().name() : "", styles.centeredStyle);
            }
        }
    }

    private String formatEntity(OrderDAO order) {
        if (order.getEntityNo() == null || order.getEntityNo().isBlank()) {
            return "-";
        }
        if (order.getOrderEntityType() != null && !order.getOrderEntityType().isBlank()) {
            return order.getOrderEntityType() + " " + order.getEntityNo();
        }
        return order.getEntityNo();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : "-";
    }

    private void createTextCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "-");
        cell.setCellStyle(style);
    }

    private void createNumberCell(Row row, int column, Number value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue("-");
        }
        cell.setCellStyle(style);
    }

    private void createCurrencyCell(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue(0.0);
        }
        cell.setCellStyle(style);
    }

    /**
     * Initializes consistent, reusable workbook styles.
     */
    private ExportStyles createExportStyles(Workbook workbook) {
        DataFormat dataFormat = workbook.createDataFormat();

        // Header Style: Dark Navy (#1F4E79), Bold White text, Centered
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerFont.setFontHeightInPoints((short) 10);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(headerStyle);

        // General Text Style (Left aligned)
        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setAlignment(HorizontalAlignment.LEFT);
        textStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(textStyle);

        // Centered Style (IDs, Codes, Dates, Statuses)
        CellStyle centeredStyle = workbook.createCellStyle();
        centeredStyle.setAlignment(HorizontalAlignment.CENTER);
        centeredStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(centeredStyle);

        // Currency Style (Right aligned, #,##0.00 format)
        CellStyle currencyStyle = workbook.createCellStyle();
        currencyStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));
        currencyStyle.setAlignment(HorizontalAlignment.RIGHT);
        currencyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(currencyStyle);

        return new ExportStyles(headerStyle, textStyle, centeredStyle, currencyStyle);
    }

    private void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    private record ExportStyles(
            CellStyle headerStyle,
            CellStyle textStyle,
            CellStyle centeredStyle,
            CellStyle currencyStyle
    ) {}
}
