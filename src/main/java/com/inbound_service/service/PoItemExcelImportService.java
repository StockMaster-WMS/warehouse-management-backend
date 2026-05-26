package com.inbound_service.service;

import com.common.excel.ExcelImportSupport;
import com.common.excel.ExcelRowReader;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.dto.request.AddPurchaseOrderItemRequest;
import com.inbound_service.dto.response.PoItemImportResponse;
import com.inbound_service.dto.response.PoItemImportResponse.ImportRowError;
import com.inbound_service.dto.response.PoItemResponse;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import com.inbound_service.repository.PoItemRepository;
import com.inbound_service.repository.PurchaseOrderRepository;
import com.product_service.dto.request.CreateProductRequest;
import com.product_service.dto.response.ProductResponse;
import com.product_service.repository.CategoryRepository;
import com.product_service.repository.SupplierRepository;
import com.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoItemExcelImportService {

    private static final UUID DEFAULT_CREATED_BY =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final int MAX_DATA_ROWS = 500;
    private static final int MAX_ERROR_DETAILS = 100;
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    private static final List<String> REQUIRED_COLUMNS = List.of("orderedQty");

    private static final String COLUMNS_HINT =
            "Cot bat buoc: orderedQty va mot trong cac cot productId/sku/name. "
                    + "Neu san pham chua ton tai thi can them name, baseUnit, categoryId hoac categoryCode. "
                    + "Cot tuy chon: unitPrice, barcodeEan13, supplierCode, weightKg, volumeCm3, minStockQty, "
                    + "isLotTracked, isExpiryTracked, isFrozen, isFragile, isHazmat, isHeavy, status.";

    private final ProductService productService;
    private final PoItemService poItemService;
    private final PoItemRepository poItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;

    public PoItemImportResponse importFromXlsx(UUID purchaseOrderId, MultipartFile file, UUID createdBy) {
        return importFromXlsx(purchaseOrderId, file, createdBy, null);
    }

    public PoItemImportResponse importFromXlsx(UUID purchaseOrderId, MultipartFile file, UUID createdBy,
            Collection<UUID> visibleWarehouseIds) {
        ExcelImportSupport.requireSafeXlsxFile(file, MAX_UPLOAD_BYTES);

        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay don nhap"));
        assertPoVisible(purchaseOrder, visibleWarehouseIds);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chi import dong hang khi don nhap dang o trang thai DRAFT");
        }

        UUID effectiveCreatedBy = createdBy != null ? createdBy : DEFAULT_CREATED_BY;
        List<ImportRowError> errors = new ArrayList<>();
        List<PoItemResponse> createdItems = new ArrayList<>();
        int attempted = 0;

        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "File khong co sheet du lieu");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Thieu dong tieu de cot o dong 1");
            }

            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> columns = ExcelImportSupport.parseHeaderRow(headerRow, formatter,
                    PoItemExcelImportService::resolveColumnHeader);
            ExcelImportSupport.requireColumns(columns, REQUIRED_COLUMNS, COLUMNS_HINT);
            requireProductIdentityColumn(columns);

            short lineNumber = (short) (poItemRepository.findMaxLineNumberByPurchaseOrderId(purchaseOrderId) + 1);
            int lastRow = sheet.getLastRowNum();

            for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
                if (attempted >= MAX_DATA_ROWS) {
                    addError(errors, rowIndex + 1, "Vuot qua toi da " + MAX_DATA_ROWS + " dong du lieu");
                    break;
                }
                Row row = sheet.getRow(rowIndex);
                if (row == null || ExcelImportSupport.isRowEffectivelyEmpty(row, columns, formatter, null)) {
                    continue;
                }

                attempted++;
                int excelRowNumber = rowIndex + 1;
                try {
                    ParsedRow parsed = parseRow(row, columns, formatter, effectiveCreatedBy,
                            purchaseOrder.getSupplierId());
                    ProductResponse product = findOrCreateProduct(parsed, excelRowNumber);

                    AddPurchaseOrderItemRequest request = new AddPurchaseOrderItemRequest(
                            lineNumber,
                            product.id(),
                            product.sku(),
                            product.name(),
                            parsed.orderedQty(),
                            parsed.unitPrice() != null ? parsed.unitPrice() : BigDecimal.ZERO);
                    PoItemResponse poItem = poItemService.addToPurchaseOrder(purchaseOrderId, request,
                            visibleWarehouseIds);
                    createdItems.add(poItem);
                    lineNumber++;
                } catch (AppException e) {
                    addError(errors, excelRowNumber, e.getMessage());
                } catch (Exception e) {
                    log.debug("Import PO item row {} failed", excelRowNumber, e);
                    addError(errors, excelRowNumber,
                            e.getMessage() != null ? e.getMessage() : "Loi khong xac dinh");
                }
            }
        } catch (AppException e) {
            throw e;
        } catch (IOException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Khong doc duoc file: " + e.getMessage());
        }

        int successCount = createdItems.size();
        return new PoItemImportResponse(attempted, successCount, attempted - successCount,
                createdItems, List.copyOf(errors));
    }

    private static Optional<String> resolveColumnHeader(String rawHeader) {
        String key = ExcelImportSupport.normalizeForAlias(rawHeader);
        return switch (key) {
            case "productid", "idsanpham", "uuidsanpham" -> Optional.of("productId");
            case "sku", "productsku", "masanpham", "mahang" -> Optional.of("sku");
            case "name", "ten", "tensanpham" -> Optional.of("name");
            case "categorycode", "madanhmuc" -> Optional.of("categoryCode");
            case "categoryid", "iddanhmuc", "uuiddanhmuc" -> Optional.of("categoryId");
            case "baseunit", "donvi", "dvcoban" -> Optional.of("baseUnit");
            case "barcodeean13", "barcode", "mavach", "ean13" -> Optional.of("barcodeEan13");
            case "suppliercode", "manhacungcap", "supplier" -> Optional.of("supplierCode");
            case "weightkg", "cannangkg" -> Optional.of("weightKg");
            case "volumecm3", "thetichcm3" -> Optional.of("volumeCm3");
            case "minstockqty", "tonmin" -> Optional.of("minStockQty");
            case "islottracked", "theolot" -> Optional.of("isLotTracked");
            case "isexpirytracked", "theohansudung", "theohsd" -> Optional.of("isExpiryTracked");
            case "isfrozen", "hangdonglanh" -> Optional.of("isFrozen");
            case "isfragile", "hangdevo" -> Optional.of("isFragile");
            case "ishazmat", "hangnguyhiem" -> Optional.of("isHazmat");
            case "isheavy", "hangnang" -> Optional.of("isHeavy");
            case "status", "trangthai" -> Optional.of("status");
            case "orderedqty", "soluongdat", "soluong", "qty" -> Optional.of("orderedQty");
            case "unitprice", "dongia", "gia" -> Optional.of("unitPrice");
            default -> Optional.empty();
        };
    }

    private static void requireProductIdentityColumn(Map<String, Integer> columns) {
        if (!columns.containsKey("productId") && !columns.containsKey("sku") && !columns.containsKey("name")) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Thieu cot dinh danh san pham: can productId hoac sku hoac name. " + COLUMNS_HINT);
        }
    }

    private record ParsedRow(
            UUID productId,
            String sku,
            CreateProductRequest productCommand,
            Integer orderedQty,
            BigDecimal unitPrice
    ) {
    }

    private ParsedRow parseRow(Row row, Map<String, Integer> columns, DataFormatter formatter,
            UUID createdBy, UUID defaultSupplierId) {
        UUID productId = parseOptionalUuid(
                ExcelRowReader.optionalString(row, columns, "productId", formatter), "productId");
        String sku = clean(ExcelRowReader.optionalString(row, columns, "sku", formatter));
        String name = clean(ExcelRowReader.optionalString(row, columns, "name", formatter));

        if (productId == null && !StringUtils.hasText(sku) && !StringUtils.hasText(name)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Can nhap productId hoac sku hoac name");
        }

        Integer orderedQty = ExcelRowReader.optionalInteger(row, columns, "orderedQty", formatter);
        if (orderedQty == null || orderedQty <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "orderedQty phai lon hon 0");
        }

        BigDecimal unitPrice = ExcelRowReader.optionalBigDecimal(row, columns, "unitPrice", formatter);
        CreateProductRequest productCommand = null;
        if (StringUtils.hasText(name)) {
            productCommand = buildCreateProductCommand(row, columns, formatter, name, createdBy, defaultSupplierId);
        }

        return new ParsedRow(productId, sku, productCommand, orderedQty, unitPrice);
    }

    private CreateProductRequest buildCreateProductCommand(Row row, Map<String, Integer> columns,
            DataFormatter formatter, String name, UUID createdBy, UUID defaultSupplierId) {
        String baseUnit = clean(ExcelRowReader.optionalString(row, columns, "baseUnit", formatter));
        UUID categoryId = resolveCategoryId(row, columns, formatter);
        UUID supplierId = resolveSupplierId(row, columns, formatter, defaultSupplierId);

        if (!StringUtils.hasText(baseUnit)) {
            return null;
        }
        if (categoryId == null) {
            return null;
        }

        return new CreateProductRequest(
                clean(ExcelRowReader.optionalString(row, columns, "barcodeEan13", formatter)),
                name,
                categoryId,
                supplierId,
                baseUnit,
                ExcelRowReader.optionalBigDecimal(row, columns, "weightKg", formatter),
                ExcelRowReader.optionalBigDecimal(row, columns, "volumeCm3", formatter),
                ExcelRowReader.optionalInteger(row, columns, "minStockQty", formatter),
                ExcelRowReader.optionalBoolean(row, columns, "isLotTracked", formatter),
                ExcelRowReader.optionalBoolean(row, columns, "isExpiryTracked", formatter),
                ExcelRowReader.optionalBoolean(row, columns, "isFrozen", formatter),
                ExcelRowReader.optionalBoolean(row, columns, "isFragile", formatter),
                ExcelRowReader.optionalBoolean(row, columns, "isHazmat", formatter),
                ExcelRowReader.optionalBoolean(row, columns, "isHeavy", formatter),
                clean(ExcelRowReader.optionalString(row, columns, "status", formatter)),
                createdBy);
    }

    private ProductResponse findOrCreateProduct(ParsedRow parsed, int excelRow) {
        if (parsed.productId() != null) {
            return productService.findById(parsed.productId());
        }

        if (StringUtils.hasText(parsed.sku())) {
            try {
                return productService.findBySku(parsed.sku());
            } catch (AppException e) {
                if (e.getErrorCode() != ErrorCode.RESOURCE_NOT_FOUND) {
                    throw e;
                }
            }
        }

        if (parsed.productCommand() != null && StringUtils.hasText(parsed.productCommand().name())) {
            try {
                return productService.findByName(parsed.productCommand().name());
            } catch (AppException e) {
                if (e.getErrorCode() != ErrorCode.RESOURCE_NOT_FOUND) {
                    throw e;
                }
            }
            log.debug("Row {}: product '{}' not found, creating new product",
                    excelRow, parsed.productCommand().name());
            return createProduct(parsed.productCommand());
        }

        throw new AppException(ErrorCode.BAD_REQUEST,
                "Khong tim thay san pham. Neu muon tao san pham moi, file can co name, baseUnit va categoryId/categoryCode");
    }

    private ProductResponse createProduct(CreateProductRequest command) {
        try {
            return productService.create(command);
        } catch (AppException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tao san pham that bai: " + e.getMessage());
        } catch (Exception e) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "Tao san pham that bai");
        }
    }

    private UUID resolveCategoryId(Row row, Map<String, Integer> columns, DataFormatter formatter) {
        UUID categoryId = parseOptionalUuid(
                ExcelRowReader.optionalString(row, columns, "categoryId", formatter), "categoryId");
        if (categoryId != null) {
            return categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                            "Khong tim thay danh muc: " + categoryId))
                    .getId();
        }

        String categoryCode = clean(ExcelRowReader.optionalString(row, columns, "categoryCode", formatter));
        if (!StringUtils.hasText(categoryCode)) {
            return null;
        }
        return categoryRepository.findByCode(categoryCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Khong tim thay danh muc theo ma: " + categoryCode))
                .getId();
    }

    private UUID resolveSupplierId(Row row, Map<String, Integer> columns, DataFormatter formatter,
            UUID defaultSupplierId) {
        String supplierCode = clean(ExcelRowReader.optionalString(row, columns, "supplierCode", formatter));
        if (!StringUtils.hasText(supplierCode)) {
            return defaultSupplierId;
        }
        return supplierRepository.findByCode(supplierCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Khong tim thay nha cung cap theo ma: " + supplierCode))
                .getId();
    }

    private static UUID parseOptionalUuid(String raw, String fieldName) {
        String value = clean(raw);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, fieldName + " khong phai UUID hop le: " + value);
        }
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void assertPoVisible(PurchaseOrder purchaseOrder, Collection<UUID> visibleWarehouseIds) {
        if (visibleWarehouseIds != null && (visibleWarehouseIds.isEmpty()
                || !visibleWarehouseIds.contains(purchaseOrder.getWarehouseId()))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Ban khong duoc phan quyen thao tac kho nay");
        }
    }

    private static void addError(List<ImportRowError> errors, int rowNumber, String message) {
        if (errors.size() >= MAX_ERROR_DETAILS) {
            return;
        }
        errors.add(new ImportRowError(rowNumber, message));
    }
}
