package com.warehouse_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.dto.response.ProductSummaryResponse;
import com.product_service.service.ProductService;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.repository.StockLevelSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
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
public class StockLevelExcelExportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StockLevelRepository stockLevelRepository;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public byte[] exportOnHandToXlsx(UUID warehouseId, UUID locationId, UUID productId) {
        Specification<StockLevel> spec = StockLevelSpecification.hasWarehouseId(warehouseId)
                .and(StockLevelSpecification.hasLocationId(locationId))
                .and(StockLevelSpecification.hasProductId(productId));

        Page<StockLevel> page = stockLevelRepository.findAll(spec, Pageable.unpaged());
        List<StockLevel> stocks = new ArrayList<>(page.getContent());

        Map<UUID, ProductSummaryResponse> productMap = loadProducts(stocks);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet dataSheet = workbook.createSheet("Data");
            Sheet metaSheet = workbook.createSheet("Metadata");

            String[] headers = {
                    "warehouseCode", "warehouseName", "locationCode", "zone", "aisle", "rack", "level", "bin",
                    "locationType", "status", "productId", "productSku", "productName", "minQty",
                    "lotNumber", "expiryDate", "qtyOnHand", "qtyReserved", "qtyAvailable", "updatedAt"
            };

            Row headerRow = dataSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIndex = 1;
            for (StockLevel stock : stocks) {
                ProductSummaryResponse product = productMap.get(stock.getProductId());
                Row row = dataSheet.createRow(rowIndex++);
                int col = 0;
                row.createCell(col++).setCellValue(stock.getWarehouse() == null ? "" : nvl(stock.getWarehouse().getCode()));
                row.createCell(col++).setCellValue(stock.getWarehouse() == null ? "" : nvl(stock.getWarehouse().getName()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getCode()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getZone()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getAisle()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getRack()));
                setShortCell(row, col++, stock.getLocation() == null ? null : stock.getLocation().getLevel());
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getBin()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getLocationType()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getStatus()));
                row.createCell(col++).setCellValue(stock.getProductId() == null ? "" : stock.getProductId().toString());
                row.createCell(col++).setCellValue(product == null ? "" : nvl(product.sku()));
                row.createCell(col++).setCellValue(product == null ? "" : nvl(product.name()));
                setIntCell(row, col++, product == null ? null : product.minQty());
                row.createCell(col++).setCellValue(nvl(stock.getLotNumber()));
                row.createCell(col++).setCellValue(stock.getExpiryDate() == null ? "" : stock.getExpiryDate().toString());
                setIntCell(row, col++, stock.getQtyOnHand());
                setIntCell(row, col++, stock.getQtyReserved());
                setIntCell(row, col++, stock.getQtyAvailable());
                row.createCell(col++).setCellValue(stock.getUpdatedAt() == null ? "" : DATE_TIME_FORMATTER.format(stock.getUpdatedAt()));
            }

            for (int i = 0; i < headers.length; i++) {
                dataSheet.autoSizeColumn(i);
            }

            writeMetadata(metaSheet, warehouseId, locationId, productId, stocks.size());
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tạo được file Excel: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportNearExpiryToXlsx(UUID warehouseId, UUID locationId, UUID productId, Integer days) {
        int horizonDays = days == null || days < 0 ? 30 : days;
        LocalDate threshold = LocalDate.now().plusDays(horizonDays);

        Specification<StockLevel> spec = StockLevelSpecification.hasWarehouseId(warehouseId)
                .and(StockLevelSpecification.hasLocationId(locationId))
                .and(StockLevelSpecification.hasProductId(productId));

        Page<StockLevel> page = stockLevelRepository.findAll(spec, Pageable.unpaged());
        List<StockLevel> stocks = page.getContent().stream()
                .filter(stock -> stock.getExpiryDate() != null)
                .filter(stock -> !stock.getExpiryDate().isAfter(threshold))
                .sorted((left, right) -> left.getExpiryDate().compareTo(right.getExpiryDate()))
                .toList();

        Map<UUID, ProductSummaryResponse> productMap = loadProducts(stocks);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet dataSheet = workbook.createSheet("Data");
            Sheet metaSheet = workbook.createSheet("Metadata");

            String[] headers = {
                    "warehouseCode", "warehouseName", "locationCode", "zone", "aisle", "rack", "bin",
                    "productId", "productSku", "productName", "lotNumber", "expiryDate", "daysLeft",
                    "qtyOnHand", "qtyReserved", "qtyAvailable", "updatedAt"
            };

            Row headerRow = dataSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIndex = 1;
            for (StockLevel stock : stocks) {
                ProductSummaryResponse product = productMap.get(stock.getProductId());
                Row row = dataSheet.createRow(rowIndex++);
                int col = 0;
                row.createCell(col++).setCellValue(stock.getWarehouse() == null ? "" : nvl(stock.getWarehouse().getCode()));
                row.createCell(col++).setCellValue(stock.getWarehouse() == null ? "" : nvl(stock.getWarehouse().getName()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getCode()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getZone()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getAisle()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getRack()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getBin()));
                row.createCell(col++).setCellValue(stock.getProductId() == null ? "" : stock.getProductId().toString());
                row.createCell(col++).setCellValue(product == null ? "" : nvl(product.sku()));
                row.createCell(col++).setCellValue(product == null ? "" : nvl(product.name()));
                row.createCell(col++).setCellValue(nvl(stock.getLotNumber()));
                row.createCell(col++).setCellValue(stock.getExpiryDate() == null ? "" : stock.getExpiryDate().toString());
                row.createCell(col++).setCellValue(String.valueOf(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), stock.getExpiryDate())));
                setIntCell(row, col++, stock.getQtyOnHand());
                setIntCell(row, col++, stock.getQtyReserved());
                setIntCell(row, col++, stock.getQtyAvailable());
                row.createCell(col++).setCellValue(stock.getUpdatedAt() == null ? "" : DATE_TIME_FORMATTER.format(stock.getUpdatedAt()));
            }

            for (int i = 0; i < headers.length; i++) {
                dataSheet.autoSizeColumn(i);
            }

            writeMetadata(metaSheet, warehouseId, locationId, productId, stocks.size(), horizonDays);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tạo được file Excel: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportLowStockToXlsx(UUID warehouseId, UUID locationId, UUID productId) {
        Specification<StockLevel> spec = StockLevelSpecification.hasWarehouseId(warehouseId)
                .and(StockLevelSpecification.hasLocationId(locationId))
                .and(StockLevelSpecification.hasProductId(productId));

        Page<StockLevel> page = stockLevelRepository.findAll(spec, Pageable.unpaged());
        List<StockLevel> allStocks = page.getContent();
        
        Map<UUID, ProductSummaryResponse> productMap = loadProducts(allStocks);
        
        List<StockLevel> lowStocks = allStocks.stream()
                .filter(stock -> {
                    ProductSummaryResponse p = productMap.get(stock.getProductId());
                    return p != null && p.minQty() != null && stock.getQtyAvailable() < p.minQty();
                })
                .toList();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet dataSheet = workbook.createSheet("LowStockData");
            String[] headers = {
                    "warehouseCode", "warehouseName", "locationCode", "productId", "productSku", "productName", 
                    "minQty", "qtyOnHand", "qtyReserved", "qtyAvailable", "lotNumber", "expiryDate"
            };

            Row headerRow = dataSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIndex = 1;
            for (StockLevel stock : lowStocks) {
                ProductSummaryResponse product = productMap.get(stock.getProductId());
                Row row = dataSheet.createRow(rowIndex++);
                int col = 0;
                row.createCell(col++).setCellValue(stock.getWarehouse() == null ? "" : nvl(stock.getWarehouse().getCode()));
                row.createCell(col++).setCellValue(stock.getWarehouse() == null ? "" : nvl(stock.getWarehouse().getName()));
                row.createCell(col++).setCellValue(stock.getLocation() == null ? "" : nvl(stock.getLocation().getCode()));
                row.createCell(col++).setCellValue(stock.getProductId() == null ? "" : stock.getProductId().toString());
                row.createCell(col++).setCellValue(product == null ? "" : nvl(product.sku()));
                row.createCell(col++).setCellValue(product == null ? "" : nvl(product.name()));
                setIntCell(row, col++, product == null ? null : product.minQty());
                setIntCell(row, col++, stock.getQtyOnHand());
                setIntCell(row, col++, stock.getQtyReserved());
                setIntCell(row, col++, stock.getQtyAvailable());
                row.createCell(col++).setCellValue(nvl(stock.getLotNumber()));
                row.createCell(col++).setCellValue(stock.getExpiryDate() == null ? "" : stock.getExpiryDate().toString());
            }

            for (int i = 0; i < headers.length; i++) dataSheet.autoSizeColumn(i);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tạo được file Excel: " + e.getMessage());
        }
    }

    private Map<UUID, ProductSummaryResponse> loadProducts(List<StockLevel> stocks) {
        List<UUID> ids = new ArrayList<>();
        for (StockLevel stock : stocks) {
            if (stock.getProductId() != null && !ids.contains(stock.getProductId())) {
                ids.add(stock.getProductId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            List<ProductSummaryResponse> data = productService.findSummariesByIds(ids);
            if (data == null || data.isEmpty()) {
                return Map.of();
            }
            Map<UUID, ProductSummaryResponse> map = new HashMap<>();
            for (ProductSummaryResponse item : data) {
                if (item != null && item.id() != null) {
                    map.put(item.id(), item);
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to load product summaries for stock export: {}", e.getMessage());
            return Map.of();
        }
    }

    private void writeMetadata(Sheet sheet, UUID warehouseId, UUID locationId, UUID productId, int totalRows) {
        writeMetadata(sheet, warehouseId, locationId, productId, totalRows, null);
    }

    private void writeMetadata(Sheet sheet, UUID warehouseId, UUID locationId, UUID productId, int totalRows, Integer days) {
        Object[][] rows = {
                {"exportedAt", DATE_TIME_FORMATTER.format(OffsetDateTime.now())},
                {"warehouseId", warehouseId == null ? "" : warehouseId.toString()},
                {"locationId", locationId == null ? "" : locationId.toString()},
                {"productId", productId == null ? "" : productId.toString()},
                {"days", days == null ? "" : days},
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

    private static void setShortCell(Row row, int index, Short value) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value);
        }
    }
}