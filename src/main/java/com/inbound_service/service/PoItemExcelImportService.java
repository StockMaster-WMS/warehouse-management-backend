package com.inbound_service.service;

import com.common.excel.ExcelImportSupport;
import com.common.excel.ExcelRowReader;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.dto.request.CreateProductRequest;
import com.product_service.dto.response.ProductResponse;
import com.product_service.service.ProductService;
import com.inbound_service.dto.request.CreatePoItemRequest;
import com.inbound_service.dto.response.PoItemImportResponse;
import com.inbound_service.dto.response.PoItemImportResponse.ImportRowError;
import com.inbound_service.dto.response.PoItemResponse;

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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Import sản phẩm mới từ file Excel và tự động thêm dòng PO (PoItem).
 * <p>
 * Luồng mỗi dòng Excel:
 * 1. Tạo sản phẩm mới qua product module
 * 2. Tạo PoItem với productId + SKU vừa tạo
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoItemExcelImportService {

    private static final UUID DEFAULT_CREATED_BY =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final int MAX_DATA_ROWS = 500;
    private static final int MAX_ERROR_DETAILS = 100;
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    private static final List<String> REQUIRED_COLUMNS =
            List.of("name", "baseUnit", "orderedQty");

    private static final String COLUMNS_HINT =
            "Cột bắt buộc: name (tên SP), baseUnit (đơn vị), categoryId hoặc categoryCode (danh mục), "
                    + "orderedQty (SL đặt). "
                    + "Cột tùy chọn: unitPrice, barcodeEan13, supplierCode, weightKg, lengthCm, widthCm, heightCm, "
                    + "minStockQty, isLotTracked, isExpiryTracked, status.";

    private final ProductService productService;
    private final PoItemService poItemService;
    private final com.inbound_service.repository.PoItemRepository poItemRepository;

    public PoItemImportResponse importFromXlsx(UUID purchaseOrderId, MultipartFile file, UUID createdBy) {
        ExcelImportSupport.requireSafeXlsxFile(file, MAX_UPLOAD_BYTES);

        UUID effectiveCreatedBy = createdBy != null ? createdBy : DEFAULT_CREATED_BY;
        List<ImportRowError> errors = new ArrayList<>();
        List<PoItemResponse> createdItems = new ArrayList<>();
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
                    PoItemExcelImportService::resolveColumnHeader);
            ExcelImportSupport.requireColumns(col, REQUIRED_COLUMNS, COLUMNS_HINT);
            requireCategoryColumn(col);

            int lastRow = sheet.getLastRowNum();
            short lineNumber = (short) (poItemRepository.findMaxLineNumberByPurchaseOrderId(purchaseOrderId) + 1);

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
                int excelRowNum = r + 1;

                try {
                    // 1. Parse thông tin sản phẩm + PO item từ dòng Excel
                    ParsedRow parsed = parseRow(row, col, fmt, effectiveCreatedBy);

                    // 2. Kiểm tra sản phẩm đã tồn tại chưa (theo tên), nếu chưa thì tạo mới
                    ProductResponse product = findOrCreateProduct(parsed.productCommand, excelRowNum);

                    // 3. Tạo PoItem
                    CreatePoItemRequest poItemReq = new CreatePoItemRequest(
                            purchaseOrderId,
                            lineNumber,
                            product.id(),
                            product.sku(),
                            product.name(),
                            parsed.orderedQty,
                            0,
                            parsed.unitPrice != null ? parsed.unitPrice : BigDecimal.ZERO
                    );
                    PoItemResponse poItem = poItemService.create(poItemReq);
                    createdItems.add(poItem);
                    lineNumber++;

                } catch (AppException e) {
                    addError(errors, excelRowNum, e.getMessage());
                } catch (Exception e) {
                    log.debug("Import row {} failed", excelRowNum, e);
                    String msg = e.getMessage() != null ? e.getMessage() : "Lỗi không xác định";
                    addError(errors, excelRowNum, msg);
                }
            }
        } catch (AppException e) {
            throw e;
        } catch (IOException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không đọc được file: " + e.getMessage());
        }

        int successCount = createdItems.size();
        int failureCount = attempted - successCount;
        return new PoItemImportResponse(attempted, successCount, failureCount, createdItems, List.copyOf(errors));
    }

    // ---- header resolution ----

    private static Optional<String> resolveColumnHeader(String rawHeader) {
        String k = ExcelImportSupport.normalizeForAlias(rawHeader);
        return switch (k) {
            // Product fields
            case "name", "ten", "tensanpham" -> Optional.of("name");
            case "categorycode", "madanhmuc" -> Optional.of("categoryCode");
            case "categoryid", "iddanhmuc", "uuiddanhmuc" -> Optional.of("categoryId");
            case "baseunit", "donvi", "dvcoban" -> Optional.of("baseUnit");
            case "barcodeean13", "barcode", "mavach", "ean13" -> Optional.of("barcodeEan13");
            case "suppliercode", "manhacungcap", "supplier" -> Optional.of("supplierCode");
            case "weightkg", "cannangkg" -> Optional.of("weightKg");
            case "minstockqty", "tonmin" -> Optional.of("minStockQty");
            case "islottracked", "theolot" -> Optional.of("isLotTracked");
            case "isexpirytracked" -> Optional.of("isExpiryTracked");
            case "status", "trangthai" -> Optional.of("status");
            // PO Item fields
            case "orderedqty", "soluongdat", "soluong", "qty" -> Optional.of("orderedQty");
            case "unitprice", "dongia", "gia" -> Optional.of("unitPrice");
            default -> Optional.empty();
        };
    }

    private static void requireCategoryColumn(Map<String, Integer> col) {
        if (!col.containsKey("categoryCode") && !col.containsKey("categoryId")) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Thiếu cột danh mục: cần categoryCode hoặc categoryId. " + COLUMNS_HINT);
        }
    }

    // ---- row parsing ----

    private record ParsedRow(CreateProductRequest productCommand, Integer orderedQty, BigDecimal unitPrice) {
    }

    private ParsedRow parseRow(Row row, Map<String, Integer> col, DataFormatter fmt, UUID createdBy) {
        String name = ExcelRowReader.requireString(row, col, "name", fmt).trim();
        String baseUnit = ExcelRowReader.requireString(row, col, "baseUnit", fmt).trim();

        UUID categoryId = resolveCategoryId(row, col, fmt);

        String barcode = ExcelRowReader.blankToNull(
                ExcelRowReader.optionalString(row, col, "barcodeEan13", fmt));

        // orderedQty (PO item)
        Integer orderedQty = ExcelRowReader.optionalInteger(row, col, "orderedQty", fmt);
        if (orderedQty == null || orderedQty <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng đặt (orderedQty) phải lớn hơn 0");
        }

        BigDecimal unitPrice = ExcelRowReader.optionalBigDecimal(row, col, "unitPrice", fmt);

        CreateProductRequest cmd = new CreateProductRequest(
                barcode,
                name,
                categoryId,
                null,  // primarySupplierId resolved by product module from supplierCode
                baseUnit,
                ExcelRowReader.optionalBigDecimal(row, col, "weightKg", fmt),
                null, // volumeCm3
                ExcelRowReader.optionalInteger(row, col, "minStockQty", fmt),
                ExcelRowReader.optionalBoolean(row, col, "isLotTracked", fmt),
                ExcelRowReader.optionalBoolean(row, col, "isExpiryTracked", fmt),
                null, // isFrozen
                null, // isFragile
                null, // isHazmat
                null, // isHeavy
                ExcelRowReader.blankToNull(ExcelRowReader.optionalString(row, col, "status", fmt)),
                createdBy
        );

        return new ParsedRow(cmd, orderedQty, unitPrice);
    }

    private UUID resolveCategoryId(Row row, Map<String, Integer> col, DataFormatter fmt) {
        // Ưu tiên categoryId (UUID)
        String idRaw = col.containsKey("categoryId")
                ? ExcelRowReader.optionalString(row, col, "categoryId", fmt)
                : null;
        if (idRaw != null && !idRaw.isBlank()) {
            try {
                return UUID.fromString(idRaw.trim());
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "categoryId không phải UUID hợp lệ: " + idRaw.trim());
            }
        }
        // Fallback: categoryCode -> cần product module resolve, truyền qua tên cột
        if (!col.containsKey("categoryCode")) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Cần điền categoryId (UUID) hoặc categoryCode (mã danh mục)");
        }
        // Khi dùng categoryCode, ta không thể resolve UUID ở đây (vì category thuộc product module).
        // Nên giữ categoryCode trong dữ liệu import để product module xử lý.
        throw new AppException(ErrorCode.BAD_REQUEST,
                "Cần dùng categoryId (UUID của danh mục) thay vì categoryCode. "
                        + "Lấy categoryId từ API GET /api/categories");
    }

    // ---- Direct module call ----

    private ProductResponse findOrCreateProduct(CreateProductRequest command, int excelRow) {
        try {
            ProductResponse res = productService.findByName(command.name());
            log.info("Dòng {}: Sản phẩm '{}' đã tồn tại (id={}), bỏ qua tạo mới",
                    excelRow, command.name(), res.id());
            return res;
        } catch (AppException e) {
            if (e.getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                log.debug("Dòng {}: Sản phẩm '{}' chưa tồn tại, sẽ tạo mới", excelRow, command.name());
                return createProductViaDirectService(command, excelRow);
            }
            log.warn("Dòng {}: Lỗi kiểm tra sản phẩm '{}', sẽ thử tạo mới. Lỗi: {}",
                    excelRow, command.name(), e.getMessage());
            return createProductViaDirectService(command, excelRow);
        }
    }

    private ProductResponse createProductViaDirectService(CreateProductRequest command, int excelRow) {
        try {
            return productService.create(command);
        } catch (AppException e) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Tạo sản phẩm thất bại: " + e.getMessage());
        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Tạo sản phẩm thất bại (Lỗi hệ thống)");
        }
    }

    private static void addError(List<ImportRowError> errors, int rowNumber, String message) {
        if (errors.size() >= MAX_ERROR_DETAILS) {
            return;
        }
        errors.add(new ImportRowError(rowNumber, message));
    }


}
