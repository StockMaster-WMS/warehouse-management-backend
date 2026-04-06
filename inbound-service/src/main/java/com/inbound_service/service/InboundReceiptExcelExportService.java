package com.inbound_service.service;

import com.common.api.ApiResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.client.ProductSummaryClient;
import com.inbound_service.client.ProductSummaryData;
import com.inbound_service.client.SupplierClient;
import com.inbound_service.client.SupplierClient.SupplierData;
import com.inbound_service.entity.InboundReceipt;
import com.inbound_service.entity.InboundReceiptItem;
import com.inbound_service.entity.InboundReceiptStatus;
import com.inbound_service.repository.InboundReceiptRepository;
import com.inbound_service.repository.InboundReceiptSpecification;
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
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundReceiptExcelExportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final InboundReceiptRepository receiptRepository;
    private final ProductSummaryClient productSummaryClient;
    private final SupplierClient supplierClient;

    @Transactional(readOnly = true)
    public byte[] exportToXlsx(String keyword, UUID purchaseOrderId, UUID warehouseId, InboundReceiptStatus status) {
        Specification<InboundReceipt> spec = InboundReceiptSpecification.hasKeyword(keyword)
                .and(InboundReceiptSpecification.hasPurchaseOrderId(purchaseOrderId))
                .and(InboundReceiptSpecification.hasWarehouseId(warehouseId))
                .and(InboundReceiptSpecification.hasStatus(status));

        List<InboundReceipt> receipts = receiptRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "receivedDate"));

        Map<UUID, ProductSummaryData> productMap = loadProducts(receipts);
        Map<UUID, SupplierData> supplierMap = loadSuppliers(receipts);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet dataSheet = workbook.createSheet("Data");
            Sheet metaSheet = workbook.createSheet("Metadata");

            String[] headers = {
                    "receiptNumber", "receivedDate", "poNumber", "purchaseOrderId", "supplierCode", "supplierName",
                    "warehouseId", "locationId", "status", "note", "itemLineNumber", "productId", "productSku",
                    "productName", "orderedQty", "receivedQty", "unitPrice", "itemNote"
            };

            Row headerRow = dataSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIndex = 1;
            int totalRows = 0;
            for (InboundReceipt receipt : receipts) {
                SupplierData supplier = supplierMap.get(receipt.getPurchaseOrder().getSupplierId());
                for (InboundReceiptItem item : safeItems(receipt)) {
                    ProductSummaryData product = productMap.get(item.getProductId());
                    Row row = dataSheet.createRow(rowIndex++);
                    int col = 0;
                    row.createCell(col++).setCellValue(nvl(receipt.getReceiptNumber()));
                    row.createCell(col++).setCellValue(receipt.getReceivedDate() == null ? "" : DATE_FORMATTER.format(receipt.getReceivedDate()));
                    row.createCell(col++).setCellValue(nvl(receipt.getPurchaseOrder().getPoNumber()));
                    row.createCell(col++).setCellValue(receipt.getPurchaseOrder().getId().toString());
                    row.createCell(col++).setCellValue(supplier == null ? "" : nvl(supplier.code()));
                    row.createCell(col++).setCellValue(supplier == null ? "" : nvl(supplier.name()));
                    row.createCell(col++).setCellValue(receipt.getWarehouseId().toString());
                    row.createCell(col++).setCellValue(receipt.getLocationId().toString());
                    row.createCell(col++).setCellValue(receipt.getStatus() == null ? "" : receipt.getStatus().name());
                    row.createCell(col++).setCellValue(nvl(receipt.getNote()));
                    row.createCell(col++).setCellValue(item.getPoItem() == null || item.getPoItem().getLineNumber() == null ? "" : String.valueOf(item.getPoItem().getLineNumber()));
                    row.createCell(col++).setCellValue(item.getProductId() == null ? "" : item.getProductId().toString());
                    row.createCell(col++).setCellValue(nvl(item.getProductSku()));
                    row.createCell(col++).setCellValue(product == null ? "" : nvl(product.name()));
                    setIntCell(row, col++, item.getPoItem() == null ? null : item.getPoItem().getOrderedQty());
                    setIntCell(row, col++, item.getReceivedQty());
                    setBigDecimalCell(row, col++, item.getPoItem() == null ? null : item.getPoItem().getUnitPrice());
                    row.createCell(col++).setCellValue(nvl(item.getNote()));
                    totalRows++;
                }
            }

            for (int i = 0; i < headers.length; i++) {
                dataSheet.autoSizeColumn(i);
            }

            writeMetadata(metaSheet, keyword, purchaseOrderId, warehouseId, status, totalRows);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tạo được file Excel: " + e.getMessage());
        }
    }

    private List<InboundReceiptItem> safeItems(InboundReceipt receipt) {
        return receipt.getItems() == null ? List.of() : receipt.getItems();
    }

    private Map<UUID, ProductSummaryData> loadProducts(List<InboundReceipt> receipts) {
        List<UUID> ids = new ArrayList<>();
        for (InboundReceipt receipt : receipts) {
            for (InboundReceiptItem item : safeItems(receipt)) {
                if (item.getProductId() != null && !ids.contains(item.getProductId())) {
                    ids.add(item.getProductId());
                }
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<List<ProductSummaryData>> resp = productSummaryClient.getByIds(ids);
            List<ProductSummaryData> data = resp == null ? null : resp.getData();
            if (data == null || data.isEmpty()) {
                return Map.of();
            }
            Map<UUID, ProductSummaryData> map = new HashMap<>();
            for (ProductSummaryData item : data) {
                if (item != null && item.id() != null) {
                    map.put(item.id(), item);
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to load product summaries: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<UUID, SupplierData> loadSuppliers(List<InboundReceipt> receipts) {
        Map<UUID, SupplierData> map = new LinkedHashMap<>();
        for (InboundReceipt receipt : receipts) {
            UUID supplierId = receipt.getPurchaseOrder().getSupplierId();
            if (supplierId == null || map.containsKey(supplierId)) {
                continue;
            }
            try {
                ApiResponse<SupplierData> resp = supplierClient.getById(supplierId);
                if (resp != null && resp.getData() != null) {
                    map.put(supplierId, resp.getData());
                }
            } catch (Exception e) {
                log.warn("Failed to load supplier {}: {}", supplierId, e.getMessage());
            }
        }
        return map;
    }

    private void writeMetadata(Sheet sheet, String keyword, UUID purchaseOrderId, UUID warehouseId, InboundReceiptStatus status, int totalRows) {
        Object[][] rows = {
                {"exportedAt", DATE_TIME_FORMATTER.format(java.time.OffsetDateTime.now())},
                {"keyword", keyword},
                {"purchaseOrderId", purchaseOrderId == null ? "" : purchaseOrderId.toString()},
                {"warehouseId", warehouseId == null ? "" : warehouseId.toString()},
                {"status", status},
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

    private static void setIntCell(Row row, int index, Integer value) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    private static void setBigDecimalCell(Row row, int index, BigDecimal value) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }
}