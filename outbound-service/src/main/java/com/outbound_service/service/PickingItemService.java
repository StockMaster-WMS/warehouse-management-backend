package com.outbound_service.service;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.common.api.stock.StockReserveCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.outbound_service.client.LocationClient;
import com.outbound_service.client.ProductClient;
import com.outbound_service.client.WarehouseStockData;
import com.outbound_service.client.WarehouseStockGateway;
import com.outbound_service.dto.request.CreatePickingItemRequest;
import com.outbound_service.dto.request.UpdatePickingItemRequest;
import com.outbound_service.dto.response.PickingItemDetailResponse;
import com.outbound_service.dto.response.PickingItemResponse;
import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.PickingItemStatus;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.mapper.PickingItemMapper;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.PickingItemSpecification;
import com.outbound_service.repository.SalesOrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PickingItemService {

    private final PickingItemRepository pickingItemRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final PickingItemMapper pickingItemMapper;
    private final WarehouseStockGateway warehouseStockGateway;
    private final ProductClient productClient;
    private final LocationClient locationClient;

    // Lấy danh sách picking item có phân trang và bộ lọc.
    public PagedResponse<PickingItemResponse> findAll(Pageable pageable, UUID soItemId, UUID productId,
            UUID locationId) {
        Specification<PickingItem> spec = PickingItemSpecification.hasSoItemId(soItemId)
                .and(PickingItemSpecification.hasProductId(productId))
                .and(PickingItemSpecification.hasLocationId(locationId));
        Page<PickingItem> page = pickingItemRepository.findAll(spec, pageable);
        Map<UUID, ProductClient.ProductDetailData> productCache = new HashMap<>();
        Map<UUID, LocationClient.LocationDetailData> locationCache = new HashMap<>();
        List<PickingItemResponse> rows = page.getContent().stream()
                .map(pickingItemMapper::toResponse)
                .map(row -> enrichListRow(row, productCache, locationCache))
                .toList();
        return new PagedResponse<>(
                rows,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    // Bổ sung thông tin sản phẩm và vị trí cho dòng hiển thị danh sách.
    private PickingItemResponse enrichListRow(PickingItemResponse row,
            Map<UUID, ProductClient.ProductDetailData> productCache,
            Map<UUID, LocationClient.LocationDetailData> locationCache) {
        ProductClient.ProductDetailData product = productCache.computeIfAbsent(
                row.productId(), this::loadProductSafe);
        LocationClient.LocationDetailData location = locationCache.computeIfAbsent(
                row.locationId(), this::loadLocationSafe);

        String productSku = row.productSku();
        if ((productSku == null || productSku.isBlank()) && product != null) {
            productSku = product.sku();
        }

        return new PickingItemResponse(
                row.id(),
                row.soItemId(),
                row.productId(),
                row.locationId(),
                row.lotNumber(),
                row.qtyToPick(),
                row.qtyPicked(),
                row.status(),
                row.pickSequence(),
                row.salesOrderNumber(),
                productSku,
                product == null ? null : product.name(),
                product == null ? null : product.barcodeEan13(),
                location == null ? null : location.code(),
                location == null ? null : location.name());
    }

    // Tải thông tin sản phẩm theo hướng an toàn, không làm hỏng luồng danh sách.
    private ProductClient.ProductDetailData loadProductSafe(UUID productId) {
        try {
            ApiResponse<ProductClient.ProductDetailData> productResp = productClient.getProductById(productId);
            return productResp == null ? null : productResp.getData();
        } catch (Exception e) {
            // Keep list resilient when product-service is temporarily unavailable.
            log.warn("Failed to load product details for productId={}: {}", productId, e.getMessage());
            return null;
        }
    }

    // Tải thông tin vị trí theo hướng an toàn, không làm hỏng luồng danh sách.
    private LocationClient.LocationDetailData loadLocationSafe(UUID locationId) {
        try {
            ApiResponse<LocationClient.LocationDetailData> locationResp = locationClient.getLocationById(locationId);
            return locationResp == null ? null : locationResp.getData();
        } catch (Exception e) {
            // Keep list resilient when warehouse location lookup fails.
            log.warn("Failed to load location details for locationId={}: {}", locationId, e.getMessage());
            return null;
        }
    }
    // Tạo mới picking item và cộng lượng reserved tương ứng.
    @Transactional
    public PickingItemResponse create(CreatePickingItemRequest request) {
        SalesOrderItem line = salesOrderItemRepository.findByIdWithSalesOrder(request.soItemId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn xuất"));
        if (!line.getProductId().equals(request.productId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "productId không khớp với dòng đơn xuất");
        }

        SalesOrder so = line.getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);

        PickingItemStatus status = parsePickingStatus(request.status());
        validateQuantities(request.qtyToPick(), request.qtyPicked(), status);
        validateAllocationAgainstOrderedQty(line, request.qtyToPick(), request.qtyPicked(), null);

        PickingItem item = pickingItemMapper.toEntity(request);
        item.setSoItem(line);
        item.setStatus(status);

        PickingItem saved = pickingItemRepository.save(item);

        if (saved.getStatus() == PickingItemStatus.PICKED) {
            int picked = saved.getQtyPicked() == null ? 0 : saved.getQtyPicked();
            warehouseStockGateway.requireOnHandAtLeast(
                    so.getWarehouseId(), saved.getLocationId(), saved.getProductId(), saved.getLotNumber(), picked);
        }

        try {
            warehouseStockGateway.adjustReservedOrThrow(reserveCommand(
                    so, request.locationId(), request.productId(), normalizeLot(request.lotNumber()),
                    request.qtyToPick(), saved.getId(), reserveMutationKey(saved.getId(), "CREATE",
                            request.locationId(), request.productId(), normalizeLot(request.lotNumber()),
                            request.qtyToPick())));
        } catch (Exception e) {
            log.error("Failed to reserve stock via Gateway: {}", e.getMessage());
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể giữ chỗ tồn kho (Reserved) tại Warehouse Service. Lỗi: " + e.getMessage());
        }

        return pickingItemMapper.toResponse(saved);
    }

    // Cập nhật picking item và điều chỉnh lại reserved khi allocation thay đổi.
    @Transactional
    public PickingItemResponse update(UUID id, UpdatePickingItemRequest request) {
        PickingItem existing = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));

        SalesOrder so = existing.getSoItem().getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);

        UUID oldLoc = existing.getLocationId();
        UUID oldProd = existing.getProductId();
        int oldQtyToPick = existing.getQtyToPick();
        String oldLot = normalizeLot(existing.getLotNumber());

        if (!existing.getSoItem().getId().equals(request.soItemId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không được chuyển picking sang dòng đơn xuất khác");
        }

        SalesOrderItem line = existing.getSoItem();
        if (!line.getProductId().equals(request.productId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "productId không khớp với dòng đơn xuất");
        }

        pickingItemMapper.updateEntity(request, existing);
        existing.setSoItem(line);
        PickingItemStatus newStatus = parsePickingStatus(request.status());
        existing.setStatus(newStatus);

        validateQuantities(existing.getQtyToPick(), existing.getQtyPicked(), newStatus);
        validateAllocationAgainstOrderedQty(line, existing.getQtyToPick(), existing.getQtyPicked(), existing.getId());

        String newLot = normalizeLot(existing.getLotNumber());
        boolean allocChanged = !oldLoc.equals(existing.getLocationId())
                || !oldProd.equals(existing.getProductId())
                || oldQtyToPick != existing.getQtyToPick()
                || !oldLot.equals(newLot);

        if (existing.getStatus() == PickingItemStatus.PICKED) {
            int picked = existing.getQtyPicked() == null ? 0 : existing.getQtyPicked();
            warehouseStockGateway.requireOnHandAtLeast(
                    so.getWarehouseId(), existing.getLocationId(), existing.getProductId(), newLot, picked);
        }

        if (allocChanged) {
            warehouseStockGateway.adjustReservedOrThrow(reserveCommand(
                    so, oldLoc, oldProd, oldLot, -oldQtyToPick,
                    existing.getId(),
                    reserveMutationKey(existing.getId(), "UPDATE_RELEASE", oldLoc, oldProd, oldLot, oldQtyToPick)));
            warehouseStockGateway.adjustReservedOrThrow(reserveCommand(
                    so, existing.getLocationId(), existing.getProductId(), newLot, existing.getQtyToPick(),
                    existing.getId(),
                    reserveMutationKey(existing.getId(), "UPDATE_RESERVE",
                            existing.getLocationId(), existing.getProductId(), newLot, existing.getQtyToPick())));
        }

        return pickingItemMapper.toResponse(pickingItemRepository.save(existing));
    }

    // Xóa picking item và hoàn trả lượng reserved đã giữ.
    @Transactional
    public void delete(UUID id) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));

        SalesOrder so = item.getSoItem().getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);

        warehouseStockGateway.adjustReservedOrThrow(reserveCommand(
                so, item.getLocationId(), item.getProductId(), normalizeLot(item.getLotNumber()),
                -item.getQtyToPick(),
                item.getId(),
                reserveMutationKey(item.getId(), "DELETE", item.getLocationId(), item.getProductId(),
                        normalizeLot(item.getLotNumber()), item.getQtyToPick())));

        pickingItemRepository.delete(item);
    }



    // Kiểm tra trạng thái đơn xuất có cho phép chỉnh sửa nghiệp vụ picking hay không.
    private static void assertSalesOrderAllowsPickingMutation(SalesOrder so) {
        SalesOrderStatus status = so.getStatus();
        // Chỉ cho phép thao tác picking khi đơn đang ở trạng thái chuẩn bị (DRAFT, PENDING) hoặc đang lấy hàng (PICKING).
        if (status == SalesOrderStatus.DRAFT || status == SalesOrderStatus.PENDING || status == SalesOrderStatus.PICKING) {
            return;
        }
        
        throw new AppException(ErrorCode.BAD_REQUEST, 
            "Không thể thao tác picking khi đơn ở trạng thái: " + status);
    }

    // Parse chuỗi trạng thái về enum PickingItemStatus.
    private static PickingItemStatus parsePickingStatus(String raw) {
        try {
            return PickingItemStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái picking không hợp lệ: " + raw);
        }
    }

    // Kiểm tra tính hợp lệ của qtyToPick, qtyPicked theo trạng thái.
    private static void validateQuantities(Integer qtyToPick, Integer qtyPicked, PickingItemStatus status) {
        if (qtyToPick == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "qtyToPick là bắt buộc");
        }
        int picked = qtyPicked == null ? 0 : qtyPicked;
        if (qtyToPick <= 0 || picked < 0 || picked > qtyToPick) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng pick không hợp lệ");
        }
        if (status == PickingItemStatus.PICKED && picked != qtyToPick) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái PICKED yêu cầu qtyPicked bằng qtyToPick");
        }
    }

    private void validateAllocationAgainstOrderedQty(SalesOrderItem line,
            Integer requestedQtyToPick, Integer requestedQtyPicked, UUID currentPickingId) {
        int orderedQty = line.getOrderedQty() == null ? 0 : line.getOrderedQty();
        int qtyToPick = requestedQtyToPick == null ? 0 : requestedQtyToPick;
        int qtyPicked = requestedQtyPicked == null ? 0 : requestedQtyPicked;

        int allocatedByOtherPicks = 0;
        int pickedByOtherPicks = 0;
        for (PickingItem pick : pickingItemRepository.findBySoItem_Id(line.getId())) {
            if (currentPickingId != null && currentPickingId.equals(pick.getId())) {
                continue;
            }

            allocatedByOtherPicks += pick.getQtyToPick() == null ? 0 : pick.getQtyToPick();
            pickedByOtherPicks += pick.getQtyPicked() == null ? 0 : pick.getQtyPicked();
        }

        int totalAllocated = allocatedByOtherPicks + qtyToPick;
        if (totalAllocated > orderedQty) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Tổng qtyToPick cho line " + line.getLineNumber()
                            + " vượt orderedQty (" + totalAllocated + "/" + orderedQty + ")");
        }

        int totalPicked = pickedByOtherPicks + qtyPicked;
        if (totalPicked > orderedQty) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Tổng qtyPicked cho line " + line.getLineNumber()
                            + " vượt orderedQty (" + totalPicked + "/" + orderedQty + ")");
        }
    }

    // Tính tồn khả dụng từ dữ liệu stock.
    private static int availableQty(WarehouseStockData r) {
        if (r.qtyAvailable() != null) {
            return r.qtyAvailable();
        }
        int on = r.qtyOnHand() == null ? 0 : r.qtyOnHand();
        int res = r.qtyReserved() == null ? 0 : r.qtyReserved();
        return Math.max(0, on - res);
    }

    // Chuẩn hóa lot number về chuỗi không null.
    private static String normalizeLot(String lotNumber) {
        return lotNumber == null ? "" : lotNumber.trim();
    }

    private static StockReserveCommand reserveCommand(SalesOrder so, UUID locationId, UUID productId,
            String lotNumber, int reservedDelta, UUID pickingItemId, String idempotencyKey) {
        return new StockReserveCommand(
                so.getWarehouseId(),
                locationId,
                productId,
                lotNumber,
                reservedDelta,
                idempotencyKey,
                "PICKING_ITEM",
                pickingItemId);
    }

    private static String reserveMutationKey(UUID pickingItemId, String action,
            UUID locationId, UUID productId, String lotNumber, int qty) {
        return "PICKING_ITEM:" + pickingItemId + ":" + action
                + ":" + locationId + ":" + productId + ":" + normalizeLot(lotNumber) + ":" + qty;
    }

    // Lấy chi tiết picking item đầy đủ dữ liệu cho giao diện picker.
    public PickingItemDetailResponse findDetailForPicker(UUID id) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));

        SalesOrderItem soItem = item.getSoItem();
        SalesOrder so = soItem.getSalesOrder();

        // Fetch product details
        ApiResponse<ProductClient.ProductDetailData> productResp;
        try {
            productResp = productClient.getProductById(item.getProductId());
        } catch (Exception e) {
            log.warn("Failed to call product-service for productId={}", item.getProductId(), e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể lấy thông tin sản phẩm");
        }
        ProductClient.ProductDetailData productData = productResp.getData();
        if (productData == null) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm");
        }

        // Fetch location details
        ApiResponse<LocationClient.LocationDetailData> locationResp;
        try {
            locationResp = locationClient.getLocationById(item.getLocationId());
        } catch (Exception e) {
            log.warn("Failed to call warehouse-service location API for locationId={}", item.getLocationId(), e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể lấy thông tin vị trí kho");
        }
        LocationClient.LocationDetailData locationData = locationResp.getData();
        if (locationData == null) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vị trí kho");
        }

        // Get current available qty at this location for this product+lot
        // Tối ưu: sử dụng getSingleStockRowIfExists thay vì listAllStocksForProduct để
        // giảm API calls
        int qtyAvailable = item.getQtyToPick();
        try {
            WarehouseStockData stock = warehouseStockGateway.getSingleStockRowIfExists(
                    so.getWarehouseId(), item.getLocationId(), item.getProductId(), item.getLotNumber());
            if (stock != null) {
                qtyAvailable = availableQty(stock);
            }
        } catch (Exception e) {
            // Keep API resilient for picker UI.
            log.warn("Failed to query stock row for pickingItemId={}: {}", item.getId(), e.getMessage());
            qtyAvailable = item.getQtyToPick();
        }

        return new PickingItemDetailResponse(
                item.getId(),
                item.getSoItem().getId(),
                so.getSoNumber(),

                productData.id(),
                productData.sku(),
                productData.name(),
                soItem.getProductSku(),
                productData.barcodeEan13(),
                productData.categoryName(),
                productData.baseUnit(),

                locationData.id(),
                locationData.code(),
                locationData.name(),
                locationData.zone(),
                locationData.aisle(),
                locationData.shelf(),
                locationData.position(),

                item.getLotNumber(),
                item.getQtyToPick(),
                item.getQtyPicked(),
                qtyAvailable,

                item.getStatus().name(),
                item.getPickSequence());
    }
}
