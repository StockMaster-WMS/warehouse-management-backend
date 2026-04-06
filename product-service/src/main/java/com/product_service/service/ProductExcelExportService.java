package com.product_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.entity.Product;
import com.product_service.repository.ProductRepository;
import com.product_service.repository.ProductSpecification;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductExcelExportService {

    private static final int MAX_EXPORT_ROWS = 10_000;

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public byte[] exportToXlsx(String keyword, UUID categoryId, String status) {
        Specification<Product> spec = ProductSpecification.hasKeyword(keyword)
                .and(ProductSpecification.hasCategory(categoryId))
                .and(ProductSpecification.hasStatus(status));
        Page<Product> page = productRepository.findAll(spec,
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.ASC, "sku")));
        if (page.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Quá nhiều bản ghi (" + page.getTotalElements() + "). Tối đa " + MAX_EXPORT_ROWS
                            + " dòng mỗi lần xuất; hãy thu hẹp bộ lọc (keyword, categoryId, status).");
        }
        List<Product> products = page.getContent();

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Products");
            String[] headers = {
                    "sku", "id", "name", "categoryCode", "categoryId", "baseUnit",
                    "barcodeEan13", "supplierCode", "weightKg",
                    "minStockQty", "isLotTracked", "isExpiryTracked", "status"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (Product p : products) {
                Row row = sheet.createRow(r++);
                int c = 0;
                row.createCell(c++).setCellValue(p.getSku());
                row.createCell(c++).setCellValue(p.getId().toString());
                row.createCell(c++).setCellValue(p.getName());
                row.createCell(c++).setCellValue(p.getCategory().getCode());
                row.createCell(c++).setCellValue(p.getCategory().getId().toString());
                row.createCell(c++).setCellValue(p.getBaseUnit());
                row.createCell(c++).setCellValue(p.getBarcodeEan13() != null ? p.getBarcodeEan13() : "");
                row.createCell(c++).setCellValue(
                        p.getPrimarySupplier() != null ? p.getPrimarySupplier().getCode() : "");
                setBigDecimalCell(row, c++, p.getWeightKg());
                if (p.getMinStockQty() != null) {
                    row.createCell(c++).setCellValue(p.getMinStockQty());
                } else {
                    row.createCell(c++);
                }
                row.createCell(c++).setCellValue(Boolean.TRUE.equals(p.getIsLotTracked()));
                row.createCell(c++).setCellValue(Boolean.TRUE.equals(p.getIsExpiryTracked()));
                row.createCell(c++).setCellValue(p.getStatus() != null ? p.getStatus() : "");
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không tạo được file Excel: " + e.getMessage());
        }
    }

    private static void setBigDecimalCell(Row row, int colIndex, BigDecimal v) {
        Cell cell = row.createCell(colIndex);
        if (v != null) {
            cell.setCellValue(v.doubleValue());
        }
    }
}
