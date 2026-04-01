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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PickingItemService {

    private static final Comparator<WarehouseStockData> FEFO_THEN_LOCATION =
            Comparator.comparing(WarehouseStockData::expiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(WarehouseStockData::locationId);

    private final PickingItemRepository pickingItemRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final PickingItemMapper pickingItemMapper;
    private final WarehouseStockGateway warehouseStockGateway;
    private final ProductClient productClient;
    private final LocationClient locationClient;

    public PagedResponse<PickingItemResponse> findAll(Pageable pageable, UUID soItemId, UUID productId, UUID locationId) {
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

    private ProductClient.ProductDetailData loadProductSafe(UUID productId) {
        try {
            ApiResponse<ProductClient.ProductDetailData> productResp = productClient.getProductById(productId);
            return productResp == null ? null : productResp.getData();
        } catch (Exception ignored) {
            // Keep list resilient when product-service is temporarily unavailable.
            return null;
        }
    }

    private LocationClient.LocationDetailData loadLocationSafe(UUID locationId) {
        try {
            ApiResponse<LocationClient.LocationDetailData> locationResp = locationClient.getLocationById(locationId);
            return locationResp == null ? null : locationResp.getData();
        } catch (Exception ignored) {
            // Keep list resilient when warehouse location lookup fails.
            return null;
        }
    }

    public PickingItemResponse findById(UUID id) {
        return pickingItemMapper.toResponse(getPickingItem(id));
    }

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

        PickingItem item = pickingItemMapper.toEntity(request);
        item.setSoItem(line);
        item.setStatus(status);

        PickingItem saved = pickingItemRepository.save(item);

        warehouseStockGateway.adjustReservedOrThrow(new StockReserveCommand(
                so.getWarehouseId(), request.locationId(), request.productId(), normalizeLot(request.lotNumber()),
                request.qtyToPick()));

        if (saved.getStatus() == PickingItemStatus.PICKED) {
            int picked = saved.getQtyPicked() == null ? 0 : saved.getQtyPicked();
            warehouseStockGateway.requireOnHandAtLeast(
                    so.getWarehouseId(), saved.getLocationId(), saved.getProductId(), saved.getLotNumber(), picked);
        }

        return pickingItemMapper.toResponse(saved);
    }

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

        SalesOrderItem line = salesOrderItemRepository.findByIdWithSalesOrder(request.soItemId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn xuất"));
        if (!line.getProductId().equals(request.productId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "productId không khớp với dòng đơn xuất");
        }

        pickingItemMapper.updateEntity(request, existing);
        existing.setSoItem(line);
        PickingItemStatus newStatus = parsePickingStatus(request.status());
        existing.setStatus(newStatus);

        validateQuantities(existing.getQtyToPick(), existing.getQtyPicked(), newStatus);

        String newLot = normalizeLot(existing.getLotNumber());
        boolean allocChanged = !oldLoc.equals(existing.getLocationId())
                || !oldProd.equals(existing.getProductId())
                || oldQtyToPick != existing.getQtyToPick()
                || !oldLot.equals(newLot);

        if (allocChanged) {
            warehouseStockGateway.adjustReservedOrThrow(new StockReserveCommand(
                    so.getWarehouseId(), oldLoc, oldProd, oldLot, -oldQtyToPick));
            warehouseStockGateway.adjustReservedOrThrow(new StockReserveCommand(
                    so.getWarehouseId(), existing.getLocationId(), existing.getProductId(), newLot,
                    existing.getQtyToPick()));
        }

        if (existing.getStatus() == PickingItemStatus.PICKED) {
            int picked = existing.getQtyPicked() == null ? 0 : existing.getQtyPicked();
            warehouseStockGateway.requireOnHandAtLeast(
                    so.getWarehouseId(), existing.getLocationId(), existing.getProductId(), newLot, picked);
        }

        return pickingItemMapper.toResponse(pickingItemRepository.save(existing));
    }

    @Transactional
    public void delete(UUID id) {
        PickingItem item = pickingItemRepository.findByIdWithSoAndOrder(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));

        SalesOrder so = item.getSoItem().getSalesOrder();
        assertSalesOrderAllowsPickingMutation(so);

        warehouseStockGateway.adjustReservedOrThrow(new StockReserveCommand(
                so.getWarehouseId(), item.getLocationId(), item.getProductId(), normalizeLot(item.getLotNumber()),
                -item.getQtyToPick()));

        pickingItemRepository.delete(item);
    }

    private PickingItem getPickingItem(UUID id) {
        return pickingItemRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy picking item"));
    }

    private static void assertSalesOrderAllowsPickingMutation(SalesOrder so) {
        if (so.getStatus() == SalesOrderStatus.PACKED || so.getStatus() == SalesOrderStatus.SHIPPED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không thao tác picking khi đơn đã đóng gói hoặc đã giao");
        }
        if (so.getStatus() == SalesOrderStatus.PICKED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thao tác picking khi đơn đã pick xong");
        }
    }

    private static PickingItemStatus parsePickingStatus(String raw) {
        try {
            return PickingItemStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái picking không hợp lệ: " + raw);
        }
    }

    private static void validateQuantities(Integer qtyToPick, Integer qtyPicked, PickingItemStatus status) {
        int picked = qtyPicked == null ? 0 : qtyPicked;
        if (qtyToPick <= 0 || picked < 0 || picked > qtyToPick) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng pick không hợp lệ");
        }
        if (status == PickingItemStatus.PICKED && picked != qtyToPick) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái PICKED yêu cầu qtyPicked bằng qtyToPick");
        }
    }

    /**
     * Tự tạo các dòng picking theo tồn khả dụng (FEFO theo hạn dùng, rồi theo vị trí), chia nhiều vị trí/lô nếu cần.
     */
    @Transactional
    public void allocatePickingLinesForNewSoItem(SalesOrderItem line) {
        SalesOrder so = line.getSalesOrder();
        if (so.getStatus() != SalesOrderStatus.PENDING) {
            return;
        }
        int need = line.getOrderedQty();
        if (need <= 0) {
            return;
        }
        UUID warehouseId = so.getWarehouseId();
        UUID productId = line.getProductId();

        List<WarehouseStockData> rows = warehouseStockGateway.listAllStocksForProduct(warehouseId, productId).stream()
                .filter(r -> availableQty(r) > 0)
                .sorted(FEFO_THEN_LOCATION)
                .toList();

        long totalAvail = rows.stream().mapToLong(PickingItemService::availableQty).sum();
        if (totalAvail < need) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không đủ tồn khả dụng để tự tạo picking (cần " + need + ", khả dụng " + totalAvail + ")");
        }

        int remaining = need;
        int seq = 1;
        for (WarehouseStockData r : rows) {
            if (remaining <= 0) {
                break;
            }
            int av = availableQty(r);
            if (av <= 0) {
                continue;
            }
            int take = Math.min(remaining, av);
            CreatePickingItemRequest req = new CreatePickingItemRequest(
                    line.getId(),
                    line.getProductId(),
                    r.locationId(),
                    take,
                    0,
                    PickingItemStatus.PENDING.name(),
                    seq,
                    normalizeLot(r.lotNumber()));
            create(req);
            seq++;
            remaining -= take;
        }
    }

    private static int availableQty(WarehouseStockData r) {
        if (r.qtyAvailable() != null) {
            return r.qtyAvailable();
        }
        int on = r.qtyOnHand() == null ? 0 : r.qtyOnHand();
        int res = r.qtyReserved() == null ? 0 : r.qtyReserved();
        return Math.max(0, on - res);
    }

    private static String normalizeLot(String lotNumber) {
        return lotNumber == null ? "" : lotNumber.trim();
    }

    /**
     * Lấy chi tiết picking item dầy đủ thông tin cho giao diện picker:
     * - Thông tin sản phẩm (SKU, tên, barcode)
     * - Thông tin vị trí (code, zone, aisle, shelf, position)
     * - Thông tin tồn khả dụng tại vị trí
     */
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
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi gọi product-service: " + e.getMessage());
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
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi gọi warehouse-service (location): " + e.getMessage());
        }
        LocationClient.LocationDetailData locationData = locationResp.getData();
        if (locationData == null) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vị trí kho");
        }

        // Get current available qty at this location for this product+lot
        // Tối ưu: sử dụng getSingleStockRowIfExists thay vì listAllStocksForProduct để giảm API calls
        int qtyAvailable = item.getQtyToPick();
        try {
            WarehouseStockData stock = warehouseStockGateway.getSingleStockRowIfExists(
                    so.getWarehouseId(), item.getLocationId(), item.getProductId(), item.getLotNumber());
            if (stock != null) {
                qtyAvailable = availableQty(stock);
            }
        } catch (Exception e) {
            // Log nhưng không throw lỗi, vì giao diện vẫn cần hiển thị picking item
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
                item.getPickSequence()
        );
    }
}
