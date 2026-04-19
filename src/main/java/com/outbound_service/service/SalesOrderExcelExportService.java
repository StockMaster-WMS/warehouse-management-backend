package com.outbound_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.service.ProductService;
import com.product_service.dto.response.ProductResponse;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.repository.SalesOrderItemRepository;
import com.outbound_service.repository.SalesOrderRepository;
import com.outbound_service.repository.SalesOrderSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesOrderExcelExportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public byte[] exportToXlsx(String keyword, String status, UUID warehouseId) {
        Specification<SalesOrder> spec = SalesOrderSpecification.hasKeyword(keyword)
                .and(SalesOrderSpecification.hasStatus(status))
                .and(SalesOrderSpecification.hasWarehouseId(warehouseId));

        List<SalesOrder> orders = salesOrderRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<UUID, ProductResponse> productMap = loadProducts(orders);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet dataSheet = workbook.createSheet("Data");
            Sheet metaSheet = workbook.createSheet("Metadata");

            String[] headers = {
                    "soNumber", "createdAt", "warehouseId", "customerName", "priority", "status",
                    "shippingAddress", "lineNumber", "productId", "productSku", "productName",
                    "orderedQty", "shippedQty", "unitPrice"
            };

            Row headerRow = dataSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIndex = 1;
            int totalRows = 0;
            for (SalesOrder order : orders) {
                List<SalesOrderItem> items = safeItems(order);
                if (items.isEmpty()) {
                    Row row = dataSheet.createRow(rowIndex++);
                    writeOrderColumns(row, order, null, null, productMap);
                    totalRows++;
                    continue;
                }
                for (SalesOrderItem item : items) {
                    Row row = dataSheet.createRow(rowIndex++);
                    writeOrderColumns(row, order, item, productMap.get(item.getProductId()), productMap);
                    totalRows++;
                }
            }

            for (int i = 0; i < headers.length; i++) {
                dataSheet.autoSizeColumn(i);
            }

            writeMetadata(metaSheet, keyword, status, warehouseId, totalRows);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tạo được file Excel: " + e.getMessage());
        }
    }

    private void writeOrderColumns(Row row, SalesOrder order, SalesOrderItem item,
            ProductResponse product,
            Map<UUID, ProductResponse> productMap) {
        int col = 0;
        row.createCell(col++).setCellValue(nvl(order.getSoNumber()));
        row.createCell(col++).setCellValue(order.getCreatedAt() == null ? "" : DATE_TIME_FORMATTER.format(order.getCreatedAt()));
        row.createCell(col++).setCellValue(order.getWarehouseId() == null ? "" : order.getWarehouseId().toString());
        row.createCell(col++).setCellValue(nvl(order.getCustomerName()));
        setShortCell(row, col++, order.getPriority());
        row.createCell(col++).setCellValue(order.getStatus() == null ? "" : order.getStatus().name());
        row.createCell(col++).setCellValue(order.getShippingAddress() == null ? "" : String.valueOf(order.getShippingAddress()));

        if (item == null) {
            for (int i = 0; i < 7; i++) {
                row.createCell(col++).setCellValue("");
            }
            return;
        }

        row.createCell(col++).setCellValue(item.getLineNumber() == null ? "" : String.valueOf(item.getLineNumber()));
        row.createCell(col++).setCellValue(item.getProductId() == null ? "" : item.getProductId().toString());
        row.createCell(col++).setCellValue(nvl(item.getProductSku()));
        row.createCell(col++).setCellValue(product == null ? "" : nvl(product.name()));
        setIntCell(row, col++, item.getOrderedQty());
        setIntCell(row, col++, item.getShippedQty());
        setBigDecimalCell(row, col++, item.getUnitPrice());
    }

    private List<SalesOrderItem> safeItems(SalesOrder order) {
        return salesOrderItemRepository.findBySalesOrder_Id(order.getId());
    }

    private Map<UUID, ProductResponse> loadProducts(List<SalesOrder> orders) {
        List<UUID> ids = new ArrayList<>();
        for (SalesOrder order : orders) {
            for (SalesOrderItem item : safeItems(order)) {
                if (item.getProductId() != null && !ids.contains(item.getProductId())) {
                    ids.add(item.getProductId());
                }
            }
        }
        Map<UUID, ProductResponse> map = new HashMap<>();
        for (UUID id : ids) {
            try {
                ProductResponse resp = productService.findById(id);
                if (resp != null) {
                    map.put(id, resp);
                }
            } catch (Exception e) {
                log.warn("Failed to load product {} for sales order export: {}", id, e.getMessage());
            }
        }
        return map;
    }

    private void writeMetadata(Sheet sheet, String keyword, String status, UUID warehouseId, int totalRows) {
        Object[][] rows = {
                {"exportedAt", DATE_TIME_FORMATTER.format(OffsetDateTime.now())},
                {"keyword", keyword},
                {"status", status},
                {"warehouseId", warehouseId == null ? "" : warehouseId.toString()},
                {"totalRows", totalRows}
        };
        for (int i = 0; i < rows.length; i++) {
            Row row = sheet.createRow(i);
            row.createCell(0).setCellValue(String.valueOf(rows[i][0]));
            row.createCell(1).setCellValue(String.valueOf(rows[i][1]));
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static void setShortCell(Row row, int index, Short value) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    private static void setIntCell(Row row, int index, Integer value) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    private static void setBigDecimalCell(Row row, int index, java.math.BigDecimal value) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }
}