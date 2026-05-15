package com.common.report;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.product_service.service.ProductService;
import com.product_service.dto.response.ProductSummaryResponse;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;

@Service
@RequiredArgsConstructor
public class InventoryExcelExportService {

    private final StockLevelRepository stockLevelRepository;
    private final ProductService productService;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transactional(readOnly = true)
    public byte[] exportToXlsx() {
        List<StockLevel> levels = stockLevelRepository.findAll();
        
        List<UUID> productIds = levels.stream()
                .map(StockLevel::getProductId)
                .distinct()
                .collect(Collectors.toList());
        
        Map<UUID, String> skuMap = productService.findSummariesByIds(productIds).stream()
                .collect(Collectors.toMap(ProductSummaryResponse::id, ProductSummaryResponse::sku));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Báo cáo tồn kho");

            // Header Tiếng Việt
            String[] headers = {
                    "Kho hàng", "Vị trí", "Mã hệ thống (ID)", "Mã SKU", "Số lô", "Số lượng tồn", "Số lượng giữ chỗ", "Ngày hết hạn"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Styles
            DataFormat format = workbook.createDataFormat();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(format.getFormat("dd/mm/yyyy"));

            int rowIndex = 1;
            for (StockLevel level : levels) {
                Row row = sheet.createRow(rowIndex++);
                int col = 0;
                
                row.createCell(col++).setCellValue(level.getWarehouse() == null ? "" : level.getWarehouse().getName());
                row.createCell(col++).setCellValue(level.getLocation() == null ? "" : level.getLocation().getCode());
                row.createCell(col++).setCellValue(level.getProductId().toString());
                
                // SKU thực tế từ ProductService
                row.createCell(col++).setCellValue(skuMap.getOrDefault(level.getProductId(), "N/A"));
                
                row.createCell(col++).setCellValue(level.getLotNumber());
                
                // Định dạng kiểu Số
                row.createCell(col++, CellType.NUMERIC).setCellValue(level.getQtyOnHand() == null ? 0 : level.getQtyOnHand());
                row.createCell(col++, CellType.NUMERIC).setCellValue(level.getQtyReserved() == null ? 0 : level.getQtyReserved());
                
                // Định dạng kiểu Ngày
                if (level.getExpiryDate() != null) {
                    var cell = row.createCell(col++);
                    cell.setCellValue(java.sql.Date.valueOf(level.getExpiryDate()));
                    cell.setCellStyle(dateStyle);
                } else {
                    row.createCell(col++).setCellValue("");
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tạo được file Excel báo cáo kho: " + e.getMessage());
        }
    }
}
