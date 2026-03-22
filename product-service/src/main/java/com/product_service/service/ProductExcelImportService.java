package com.product_service.service;

import com.common.excel.ExcelImportSupport;
import com.common.excel.ExcelRowReader;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.dto.request.CreateProductRequest;
import com.product_service.dto.response.ProductImportResponse;
import com.product_service.dto.response.ProductImportResponse.ImportRowError;
import com.product_service.repository.CategoryRepository;
import com.product_service.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductExcelImportService {

    /** Giống {@link com.product_service.entity.Product} @PrePersist khi không có user cụ thể */
    private static final UUID DEFAULT_IMPORT_CREATED_BY =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final int MAX_DATA_ROWS = 2_000;
    private static final int MAX_ERROR_DETAILS = 100;

    private static final List<String> REQUIRED_BASE_COLUMNS = List.of("name", "baseUnit");
    private static final String REQUIRED_COLUMNS_HINT =
            "Dòng 1: bắt buộc name, baseUnit; và một trong hai: categoryCode (mã DM-...) hoặc categoryId (UUID). "
                    + "Nên dùng categoryId khi kéo fill Excel — mã chữ DM-... hay bị Excel tự tăng (B66C→B67C). "
                    + "Alias: ten, donVi, maDanhMuc, idDanhMuc.";

    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;

    public ProductImportResponse importFromXlsx(MultipartFile file, UUID createdBy) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File không được để trống");
        }
        ExcelImportSupport.requireXlsxExtension(file.getOriginalFilename());
        UUID effectiveCreatedBy = createdBy != null ? createdBy : DEFAULT_IMPORT_CREATED_BY;

        List<ImportRowError> errors = new ArrayList<>();
        int success = 0;
        int attempted = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "File không có sheet");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Thiếu dòng tiêu đề cột (dòng 1)");
            }
            DataFormatter fmt = new DataFormatter();
            Map<String, Integer> col = ExcelImportSupport.parseHeaderRow(headerRow, fmt,
                    ProductExcelImportService::resolveProductColumnHeader);
            ExcelImportSupport.requireColumns(col, REQUIRED_BASE_COLUMNS, REQUIRED_COLUMNS_HINT);
            requireCategoryColumnPresent(col);

            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {
                if (attempted >= MAX_DATA_ROWS) {
                    addError(errors, r + 1, "Vượt quá tối đa " + MAX_DATA_ROWS + " dòng dữ liệu");
                    break;
                }
                Row row = sheet.getRow(r);
                if (row == null || ExcelImportSupport.isRowEffectivelyEmpty(row, col, fmt, "name")) {
                    continue;
                }
                attempted++;
                int excelRow = r + 1;
                try {
                    CreateProductRequest request = parseProductRow(row, col, fmt, effectiveCreatedBy);
                    productService.create(request);
                    success++;
                } catch (AppException e) {
                    addError(errors, excelRow, e.getMessage());
                } catch (Exception e) {
                    log.debug("Import row {} failed", excelRow, e);
                    String msg = e.getMessage() != null ? e.getMessage() : "Lỗi không xác định";
                    addError(errors, excelRow, msg);
                }
            }
        } catch (AppException e) {
            throw e;
        } catch (IOException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không đọc được file: " + e.getMessage());
        }

        int failureCount = attempted - success;
        return new ProductImportResponse(attempted, success, failureCount, List.copyOf(errors));
    }

    /** Tiêu đề cột (dòng 1) → key nội bộ; alias tiếng Việt không dấu / tiếng Anh. */
    private static Optional<String> resolveProductColumnHeader(String rawHeaderCellText) {
        String k = ExcelImportSupport.normalizeForAlias(rawHeaderCellText);
        return switch (k) {
            case "name", "ten", "tensanpham" -> Optional.of("name");
            case "categorycode", "madanhmuc" -> Optional.of("categoryCode");
            case "categoryid", "iddanhmuc", "uuidanhmuc" -> Optional.of("categoryId");
            case "baseunit", "donvi", "dvcoban" -> Optional.of("baseUnit");
            case "barcodeean13", "barcode", "mavach", "ean13" -> Optional.of("barcodeEan13");
            case "suppliercode", "manhacungcap", "supplier" -> Optional.of("supplierCode");
            case "weightkg", "cannangkg" -> Optional.of("weightKg");
            case "lengthcm", "dai" -> Optional.of("lengthCm");
            case "widthcm", "rong" -> Optional.of("widthCm");
            case "heightcm", "cao" -> Optional.of("heightCm");
            case "minstockqty", "tonmin" -> Optional.of("minStockQty");
            case "islottracked", "theolot" -> Optional.of("isLotTracked");
            case "isexpirytracked" -> Optional.of("isExpiryTracked");
            case "status", "trangthai" -> Optional.of("status");
            default -> Optional.empty();
        };
    }

    private static void requireCategoryColumnPresent(Map<String, Integer> col) {
        if (!col.containsKey("categoryCode") && !col.containsKey("categoryId")) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Thiếu cột: cần categoryCode hoặc categoryId. " + REQUIRED_COLUMNS_HINT);
        }
    }

    private CreateProductRequest parseProductRow(Row row, Map<String, Integer> col, DataFormatter fmt,
            UUID createdBy) {
        String name = ExcelRowReader.requireString(row, col, "name", fmt);
        String baseUnit = ExcelRowReader.requireString(row, col, "baseUnit", fmt);

        UUID categoryId = resolveCategoryId(row, col, fmt);

        String supplierCode = ExcelRowReader.optionalString(row, col, "supplierCode", fmt);
        UUID primarySupplierId = null;
        if (supplierCode != null && !supplierCode.isBlank()) {
            primarySupplierId = supplierRepository.findByCode(supplierCode.trim())
                    .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                            "Không tìm thấy nhà cung cấp mã: " + supplierCode.trim()))
                    .getId();
        }

        String barcode = ExcelRowReader.optionalString(row, col, "barcodeEan13", fmt);
        if (barcode != null) {
            barcode = barcode.trim();
        }

        return new CreateProductRequest(
                ExcelRowReader.blankToNull(barcode),
                name.trim(),
                categoryId,
                primarySupplierId,
                baseUnit.trim(),
                ExcelRowReader.optionalBigDecimal(row, col, "weightKg", fmt),
                ExcelRowReader.optionalBigDecimal(row, col, "lengthCm", fmt),
                ExcelRowReader.optionalBigDecimal(row, col, "widthCm", fmt),
                ExcelRowReader.optionalBigDecimal(row, col, "heightCm", fmt),
                ExcelRowReader.optionalInteger(row, col, "minStockQty", fmt),
                ExcelRowReader.optionalBoolean(row, col, "isLotTracked", fmt),
                ExcelRowReader.optionalBoolean(row, col, "isExpiryTracked", fmt),
                ExcelRowReader.blankToNull(ExcelRowReader.optionalString(row, col, "status", fmt)),
                createdBy
        );
    }

    /**
     * Ưu tiên {@code categoryId} (UUID) nếu ô có giá trị — kéo fill trong Excel ít bị sai hơn mã {@code categoryCode}.
     */
    private UUID resolveCategoryId(Row row, Map<String, Integer> col, DataFormatter fmt) {
        String idRaw = col.containsKey("categoryId")
                ? ExcelRowReader.optionalString(row, col, "categoryId", fmt)
                : null;
        if (idRaw != null && !idRaw.isBlank()) {
            UUID id;
            try {
                id = UUID.fromString(idRaw.trim());
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "categoryId không phải UUID hợp lệ: " + idRaw.trim());
            }
            return categoryRepository.findById(id)
                    .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                            "Không tìm thấy danh mục id: " + id))
                    .getId();
        }
        if (!col.containsKey("categoryCode")) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Cần điền categoryId (UUID) hoặc categoryCode (mã danh mục)");
        }
        String categoryCode = ExcelRowReader.requireString(row, col, "categoryCode", fmt);
        return categoryRepository.findByCode(categoryCode.trim())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                        "Không tìm thấy danh mục mã: " + categoryCode.trim()))
                .getId();
    }

    private static void addError(List<ImportRowError> errors, int rowNumber, String message) {
        if (errors.size() >= MAX_ERROR_DETAILS) {
            return;
        }
        errors.add(new ImportRowError(rowNumber, message));
    }
}
