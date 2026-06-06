package com.inbound_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.inbound_service.dto.request.CreatePoItemRequest;
import com.inbound_service.dto.request.UpdatePoItemRequest;
import com.inbound_service.dto.response.PoItemImportResponse;
import com.inbound_service.dto.response.PoItemResponse;
import com.inbound_service.service.PoItemExcelImportService;
import com.inbound_service.service.PoItemService;
import com.warehouse_service.service.WarehouseAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/po-items")
@Tag(name = "PO Item", description = "Quan ly dong don nhap noi bo")
public class PoItemController {

    private final PoItemService poItemService;
    private final PoItemExcelImportService poItemExcelImportService;
    private final WarehouseAccessService warehouseAccessService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lay danh sach dong don nhap", description = "Phan trang; loc purchaseOrderId, keyword (SKU)")
    public ApiResponse<PagedResponse<PoItemResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lineNumber") String sort,
            @RequestParam(defaultValue = "asc") String sortDir,
            @Parameter(description = "ID don nhap") @RequestParam(required = false) UUID purchaseOrderId,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        return ApiResponse.success("Lay danh sach dong don nhap thanh cong",
                poItemService.findAll(pageable, purchaseOrderId, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Lay dong don nhap theo ID")
    public ApiResponse<PoItemResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lay dong don nhap thanh cong", poItemService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Tao dong don nhap")
    public ApiResponse<PoItemResponse> create(@Valid @RequestBody CreatePoItemRequest request,
            Authentication authentication) {
        return ApiResponse.success("Tao dong don nhap thanh cong",
                poItemService.create(request, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Cap nhat dong don nhap")
    public ApiResponse<PoItemResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdatePoItemRequest request,
            Authentication authentication) {
        return ApiResponse.success("Cap nhat dong don nhap thanh cong",
                poItemService.update(id, request, warehouseAccessService.visibleWarehouseIdSet(authentication)));
    }

    @PostMapping(value = "/import/{purchaseOrderId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(
            summary = "Import dong don nhap tu Excel",
            description = "Upload file .xlsx de them dong vao PO. Ho tro san pham co san bang productId/sku/name; neu chua co thi tao san pham moi bang name + baseUnit + categoryId/categoryCode.")
    public ApiResponse<PoItemImportResponse> importExcel(
            @PathVariable UUID purchaseOrderId,
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "UUID nguoi tao; bo trong = user hien tai") @RequestParam(required = false) UUID createdBy,
            Authentication authentication) {
        UUID effectiveCreatedBy = createdBy != null ? createdBy : currentUserId(authentication);
        PoItemImportResponse result = poItemExcelImportService.importFromXlsx(purchaseOrderId, file, effectiveCreatedBy,
                warehouseAccessService.visibleWarehouseIdSet(authentication));
        return ApiResponse.success("Import hoan tat", result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Xoa dong don nhap")
    public ApiResponse<String> delete(@PathVariable UUID id, Authentication authentication) {
        poItemService.delete(id, warehouseAccessService.visibleWarehouseIdSet(authentication));
        return ApiResponse.success("Xoa dong don nhap thanh cong", id.toString());
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.auth_service.security.JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (Exception ex) {
            return null;
        }
    }
}
