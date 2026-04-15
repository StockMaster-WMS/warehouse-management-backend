package com.product_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.entity.Supplier;
import com.product_service.repository.SupplierRepository;
import com.product_service.repository.SupplierSpecification;
import lombok.RequiredArgsConstructor;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierExcelExportService {

    private static final int MAX_EXPORT_ROWS = 10_000;

    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public byte[] exportToXlsx(String keyword, String status) {
        Specification<Supplier> spec = SupplierSpecification.hasKeyword(keyword)
                .and(SupplierSpecification.hasStatus(status));
        Page<Supplier> page = supplierRepository.findAll(spec,
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.ASC, "code")));
        if (page.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Quá nhiều bản ghi (" + page.getTotalElements() + "). Tối đa " + MAX_EXPORT_ROWS
                            + " dòng mỗi lần xuất; hãy thu hẹp bộ lọc.");
        }
        List<Supplier> suppliers = page.getContent();

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Suppliers");
            String[] headers = {
                    "code", "name", "taxCode", "contactName", "contactPhone",
                    "contactEmail", "address", "paymentTerms", "leadTimeDays", "status"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (Supplier s : suppliers) {
                Row row = sheet.createRow(r++);
                int c = 0;
                row.createCell(c++).setCellValue(s.getCode());
                row.createCell(c++).setCellValue(s.getName());
                row.createCell(c++).setCellValue(s.getTaxCode() != null ? s.getTaxCode() : "");
                row.createCell(c++).setCellValue(s.getContactName() != null ? s.getContactName() : "");
                row.createCell(c++).setCellValue(s.getContactPhone() != null ? s.getContactPhone() : "");
                row.createCell(c++).setCellValue(s.getContactEmail() != null ? s.getContactEmail() : "");
                row.createCell(c++).setCellValue(s.getAddress() != null ? s.getAddress() : "");
                if (s.getPaymentTerms() != null) {
                    row.createCell(c++).setCellValue(s.getPaymentTerms());
                } else {
                    row.createCell(c++);
                }
                if (s.getLeadTimeDays() != null) {
                    row.createCell(c++).setCellValue(s.getLeadTimeDays());
                } else {
                    row.createCell(c++);
                }
                row.createCell(c++).setCellValue(s.getStatus() != null ? s.getStatus() : "");
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
}
