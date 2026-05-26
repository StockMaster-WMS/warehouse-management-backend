package com.inbound_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.common.api.PagedResponse;
import com.common.api.stock.StockAdjustCommand;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.util.CodeGenerator;
import com.product_service.entity.Supplier;
import com.product_service.repository.SupplierRepository;
import com.product_service.repository.ProductRepository;
import com.warehouse_service.service.StockLevelService;
import com.inbound_service.dto.request.CreateInboundReceiptRequest;
import com.inbound_service.dto.request.ReceiveLineRequest;
import com.inbound_service.dto.response.InboundLocationSuggestionResponse;
import com.inbound_service.dto.response.InboundPrintItemResponse;
import com.inbound_service.dto.response.InboundPrintResponse;
import com.inbound_service.dto.response.InboundReceiptResponse;
import com.inbound_service.entity.*;
import com.inbound_service.mapper.InboundReceiptMapper;
import com.inbound_service.repository.InboundReceiptRepository;
import com.inbound_service.repository.InboundReceiptSpecification;
import com.inbound_service.repository.PoItemRepository;
import com.inbound_service.repository.PurchaseOrderRepository;
import com.inbound_service.repository.PutawayTaskRepository;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class InboundReceiptService {

    private final InboundReceiptRepository receiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PoItemRepository poItemRepository;
    private final PutawayTaskRepository putawayTaskRepository;
    private final StockLevelService stockLevelService;
    private final InboundReceiptMapper receiptMapper;
    private final AuditLogService auditLogService;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ObjectMapper objectMapper;

    private static final EnumSet<PurchaseOrderStatus> RECEIVABLE_STATUSES =
            EnumSet.of(PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.PARTIAL);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 160;

        // Tạo phiếu nhập kho và cập nhật tồn kho, trạng thái PO, putaway task.
    @Transactional
    public InboundReceiptResponse createReceipt(CreateInboundReceiptRequest request) {
        return createReceipt(request, null);
    }

    // Tạo phiếu nhập kho và cập nhật tồn kho, trạng thái PO, putaway task.
    @Transactional
    public InboundReceiptResponse createReceipt(CreateInboundReceiptRequest request, String rawIdempotencyKey) {
        return createReceipt(request, rawIdempotencyKey, null);
    }

    @Transactional
    public InboundReceiptResponse createReceipt(CreateInboundReceiptRequest request, String rawIdempotencyKey,
            Collection<UUID> visibleWarehouseIds) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phiếu nhập phải có ít nhất một dòng nhận hàng");
        }
        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        String requestHash = idempotencyKey == null ? null : requestHash(request);
        Optional<InboundReceiptResponse> existingResponse = findExistingIdempotentReceipt(idempotencyKey, requestHash);
        if (existingResponse.isPresent()) {
            return existingResponse.get();
        }

        // 1. Lấy PO và kiểm tra trạng thái
        PurchaseOrder po = purchaseOrderRepository.findByIdForUpdate(request.purchaseOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập"));

        assertWarehouseVisible(po.getWarehouseId(), visibleWarehouseIds);

        existingResponse = findExistingIdempotentReceipt(idempotencyKey, requestHash);
        if (existingResponse.isPresent()) {
            return existingResponse.get();
        }

        if (!RECEIVABLE_STATUSES.contains(po.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ nhận hàng cho đơn nhập đã duyệt (APPROVED) hoặc đang nhận dở (PARTIAL)");
        }

        // 2. Lấy danh sách dòng PO
        List<PoItem> poItems = poItemRepository.findByPurchaseOrderIdForUpdate(po.getId());
        Map<UUID, PoItem> poItemMap = new HashMap<>();
        for (PoItem item : poItems) {
            poItemMap.put(item.getId(), item);
        }

        // 3. Kiểm tra số lượng từng dòng, kể cả khi request chứa nhiều dòng cho cùng một poItem
        Map<UUID, Integer> requestedQtyByPoItem = new HashMap<>();
        Map<ReceiveLineRequest, UUID> lineLocations = new IdentityHashMap<>();
        for (ReceiveLineRequest line : request.items()) {
            if (line.receivedQty() == null || line.receivedQty() <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng nhận phải lớn hơn 0");
            }

            UUID lineLocationId = resolveLineLocationId(request, line);
            assertLocationInWarehouse(lineLocationId, po.getWarehouseId());
            lineLocations.put(line, lineLocationId);
            requestedQtyByPoItem.merge(line.poItemId(), line.receivedQty(), Integer::sum);
        }

        for (Map.Entry<UUID, Integer> entry : requestedQtyByPoItem.entrySet()) {
            PoItem poItem = poItemMap.get(entry.getKey());
            if (poItem == null) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Dòng PO " + entry.getKey() + " không thuộc đơn nhập này");
            }

            int currentReceived = poItem.getReceivedQty() == null ? 0 : poItem.getReceivedQty();
            int remaining = poItem.getOrderedQty() - currentReceived;
            int requestedQty = entry.getValue();

            if (requestedQty > remaining) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "SKU " + poItem.getProductSku() + ": tổng số lượng nhận (" + requestedQty
                                + ") vượt quá số còn lại (" + remaining
                                + "), đã nhận " + currentReceived + "/" + poItem.getOrderedQty());
            }
        }

        List<InboundReceiptItem> receiptItems = new ArrayList<>();
        for (ReceiveLineRequest line : request.items()) {
            PoItem poItem = poItemMap.get(line.poItemId());

            if (poItem == null) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Dòng PO " + line.poItemId() + " không thuộc đơn nhập này");
            }

            receiptItems.add(InboundReceiptItem.builder()
                    .poItem(poItem)
                    .productId(poItem.getProductId())
                    .productSku(poItem.getProductSku())
                    .receivedQty(line.receivedQty())
                    .locationId(lineLocations.get(line))
                    .note(line.note())
                    .build());
        }

        // 4. Tạo phiếu nhập kho
        UUID primaryLocationId = request.locationId() != null
                ? request.locationId()
                : receiptItems.get(0).getLocationId();

        InboundReceipt receipt = InboundReceipt.builder()
                .receiptNumber(generateUniqueReceiptNumber())
                .purchaseOrder(po)
                .warehouseId(po.getWarehouseId())
                .locationId(primaryLocationId)
                .status(InboundReceiptStatus.RECEIVED)
                .note(request.note())
                .receivedDate(LocalDate.now())
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .build();

        for (InboundReceiptItem item : receiptItems) {
            item.setReceipt(receipt);
        }
        receipt.setItems(receiptItems);
        receiptRepository.save(receipt);

        // 5. Cập nhật số lượng đã nhận trên dòng PO
        Set<PoItem> updatedPoItems = new LinkedHashSet<>();
        for (InboundReceiptItem receiptItem : receiptItems) {
            PoItem poItem = receiptItem.getPoItem();
            int current = poItem.getReceivedQty() == null ? 0 : poItem.getReceivedQty();
            poItem.setReceivedQty(current + receiptItem.getReceivedQty());
            updatedPoItems.add(poItem);
        }
        if (!updatedPoItems.isEmpty()) {
            poItemRepository.saveAll(updatedPoItems);
        }

        // 6. Cập nhật tồn kho qua warehouse module.
        for (InboundReceiptItem receiptItem : receiptItems) {
            StockAdjustCommand cmd = new StockAdjustCommand(
                    po.getWarehouseId(),
                    receiptItem.getLocationId(),
                    receiptItem.getProductId(),
                    null,
                    receiptItem.getReceivedQty(),
                    "INBOUND_RECEIPT_ITEM:" + receiptItem.getId() + ":ADJUST",
                    "INBOUND_RECEIPT",
                    receipt.getId());
            stockLevelService.adjust(cmd);
        }

        // 7. Cập nhật trạng thái PO (PARTIAL / COMPLETED)
        refreshPoStatus(po.getId());

        // 8. Tạo putaway tasks cho hàng vừa nhận
        for (InboundReceiptItem receiptItem : receiptItems) {
            PutawayTask task = PutawayTask.builder()
                    .poItem(receiptItem.getPoItem())
                    .inboundReceipt(receipt)
                    .productId(receiptItem.getProductId())
                    .qtyToPutaway(receiptItem.getReceivedQty())
                    .suggestedLocationId(receiptItem.getLocationId())
                    .status(PutawayStatus.PENDING)
                    .build();
            putawayTaskRepository.save(task);
        }

        InboundReceiptResponse response = receiptMapper.toResponse(receipt);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("receiptNumber", receipt.getReceiptNumber());
        metadata.put("purchaseOrderId", po.getId());
        metadata.put("poNumber", po.getPoNumber());
        metadata.put("warehouseId", po.getWarehouseId());
        metadata.put("locationId", primaryLocationId);
        metadata.put("lineLocationIds", receiptItems.stream().map(InboundReceiptItem::getLocationId).toList());
        metadata.put("lineCount", receiptItems.size());
        auditLogService.record("INBOUND_RECEIPT", "CREATE", "Tạo phiếu nhập kho",
                "INBOUND_RECEIPT", receipt.getId(), receipt.getReceiptNumber(), null, response,
                request.note(), metadata);
        return response;
    }

    // Lấy danh sách phiếu nhập có phân trang và bộ lọc.
    @Transactional(readOnly = true)
    public PagedResponse<InboundReceiptResponse> findAll(Pageable pageable, String keyword,
            UUID purchaseOrderId, UUID warehouseId, InboundReceiptStatus status,
            OffsetDateTime createdFrom, OffsetDateTime createdTo) {
        return findAll(pageable, keyword, purchaseOrderId, warehouseId, status, createdFrom, createdTo, null);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InboundReceiptResponse> findAll(Pageable pageable, String keyword,
            UUID purchaseOrderId, UUID warehouseId, InboundReceiptStatus status,
            OffsetDateTime createdFrom, OffsetDateTime createdTo, Collection<UUID> visibleWarehouseIds) {
        Specification<InboundReceipt> spec = InboundReceiptSpecification.hasKeyword(keyword)
                .and(InboundReceiptSpecification.hasPurchaseOrderId(purchaseOrderId))
                .and(InboundReceiptSpecification.hasWarehouseId(warehouseId))
                .and(InboundReceiptSpecification.warehouseIdIn(visibleWarehouseIds))
                .and(InboundReceiptSpecification.hasStatus(status))
                .and(InboundReceiptSpecification.createdFrom(createdFrom))
                .and(InboundReceiptSpecification.createdTo(createdTo));
        Page<InboundReceipt> page = receiptRepository.findAll(spec, pageable);
        Page<InboundReceiptResponse> mapped = page.map(receiptMapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    // Lấy chi tiết phiếu nhập theo id.
    @Transactional(readOnly = true)
    public InboundReceiptResponse findById(UUID id) {
        return findById(id, null);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse findById(UUID id, Collection<UUID> visibleWarehouseIds) {
        InboundReceipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiếu nhập"));
        assertWarehouseVisible(receipt.getWarehouseId(), visibleWarehouseIds);
        return receiptMapper.toResponse(receipt);
    }

    // Lấy dữ liệu in phiếu nhập kho (Packing List/GRN) chuyên dụng
    @Transactional(readOnly = true)
    public InboundPrintResponse getPrintData(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiếu nhập"));

        PurchaseOrder po = receipt.getPurchaseOrder();

        String supplierName = "";
        String supplierAddress = "";
        String supplierPhone = "";

        try {
            Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
            if (supplier != null) {
                supplierName = supplier.getName();
                supplierAddress = supplier.getAddress();
                supplierPhone = supplier.getContactPhone();
            }
        } catch (Exception e) {
            // fallback
        }

        List<InboundPrintItemResponse> itemResponses = new ArrayList<>();
        short stt = 1;
        for (InboundReceiptItem item : receipt.getItems()) {
            String productName = item.getProductSku();
            String unit = "";
            try {
                var product = productRepository.findById(item.getProductId()).orElse(null);
                if (product != null) {
                    productName = product.getName();
                    unit = product.getBaseUnit();
                }
            } catch (Exception e) {
                // fallback
            }

            Integer orderedQty = item.getPoItem() != null ? item.getPoItem().getOrderedQty() : item.getReceivedQty();

            itemResponses.add(new InboundPrintItemResponse(
                    stt++,
                    item.getProductId(),
                    item.getProductSku(),
                    productName,
                    unit,
                    orderedQty,
                    item.getReceivedQty(),
                    item.getNote()
            ));
        }

        return new InboundPrintResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                po.getPoNumber(),
                receipt.getWarehouseId(),
                receipt.getLocationId(),
                receipt.getReceivedDate(),
                supplierName,
                supplierAddress,
                supplierPhone,
                receipt.getReceivedBy(),
                receipt.getNote(),
                itemResponses
        );
    }

    // Lấy danh sách phiếu nhập theo đơn nhập.
    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> findByPurchaseOrderId(UUID purchaseOrderId) {
        return findByPurchaseOrderId(purchaseOrderId, null);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> findByPurchaseOrderId(UUID purchaseOrderId,
            Collection<UUID> visibleWarehouseIds) {
        return receiptRepository.findByPurchaseOrderId(purchaseOrderId).stream()
                .filter(receipt -> visibleWarehouseIds == null || visibleWarehouseIds.contains(receipt.getWarehouseId()))
                .map(receiptMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InboundLocationSuggestionResponse> suggestLocations(UUID warehouseId, UUID productId, int limit) {
        return suggestLocations(warehouseId, productId, null, limit);
    }

    @Transactional(readOnly = true)
    public List<InboundLocationSuggestionResponse> suggestLocations(UUID warehouseId, UUID productId, UUID poItemId, int limit) {
        return suggestLocations(warehouseId, productId, poItemId, limit, null);
    }

    @Transactional(readOnly = true)
    public List<InboundLocationSuggestionResponse> suggestLocations(UUID warehouseId, UUID productId, UUID poItemId,
            int limit, Collection<UUID> visibleWarehouseIds) {
        if (poItemId != null) {
            PoItem poItem = poItemRepository.findById(poItemId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay dong don nhap"));
            productId = poItem.getProductId();
            if (warehouseId == null && poItem.getPurchaseOrder() != null) {
                warehouseId = poItem.getPurchaseOrder().getWarehouseId();
            }
        }
        if (warehouseId == null || productId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Can truyen poItemId hoac cap warehouseId/productId");
        }
        assertWarehouseVisible(warehouseId, visibleWarehouseIds);
        int max = limit <= 0 ? 20 : Math.min(limit, 50);

        List<Location> allLocations = locationRepository.findByWarehouseId(warehouseId).stream()
                .filter(this::isInboundStorageLocation)
                .toList();
        Map<UUID, Location> locationById = allLocations.stream()
                .collect(java.util.stream.Collectors.toMap(Location::getId, java.util.function.Function.identity()));

        List<StockLevel> productStocks = stockLevelRepository
                .findByWarehouseIdAndProductIdWithDetails(warehouseId, productId).stream()
                .filter(stock -> stock.getLocation() != null)
                .filter(stock -> safeQty(stock.getQtyOnHand()) > 0)
                .filter(stock -> locationById.containsKey(stock.getLocation().getId()))
                .sorted(Comparator
                        .comparing((StockLevel stock) -> stock.getLocation().getCode(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(stock -> normalizeLot(stock.getLotNumber())))
                .toList();

        Set<UUID> usedLocationIds = stockLevelRepository.findByWarehouseId(warehouseId).stream()
                .filter(stock -> stock.getLocation() != null)
                .filter(stock -> safeQty(stock.getQtyOnHand()) > 0)
                .map(stock -> stock.getLocation().getId())
                .collect(java.util.stream.Collectors.toSet());

        LinkedHashMap<UUID, InboundLocationSuggestionResponse> suggestions = new LinkedHashMap<>();
        for (StockLevel stock : productStocks) {
            Location location = stock.getLocation();
            suggestions.putIfAbsent(location.getId(), toSuggestion(location, true, false, stock.getQtyOnHand()));
            if (suggestions.size() >= max) {
                return new ArrayList<>(suggestions.values());
            }
        }

        allLocations.stream()
                .filter(location -> !usedLocationIds.contains(location.getId()))
                .sorted(Comparator.comparing(Location::getCode, String.CASE_INSENSITIVE_ORDER))
                .forEach(location -> {
                    if (suggestions.size() < max) {
                        suggestions.putIfAbsent(location.getId(), toSuggestion(location, false, true, 0));
                    }
                });

        if (suggestions.size() < max) {
            allLocations.stream()
                    .sorted(Comparator.comparing(Location::getCode, String.CASE_INSENSITIVE_ORDER))
                    .forEach(location -> {
                        if (suggestions.size() < max) {
                            suggestions.putIfAbsent(location.getId(), toSuggestion(location, false, false, null));
                        }
                    });
        }

        return new ArrayList<>(suggestions.values());
    }

    // ---- helpers ----

    private Optional<InboundReceiptResponse> findExistingIdempotentReceipt(String idempotencyKey, String requestHash) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        return receiptRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    if (!Objects.equals(existing.getRequestHash(), requestHash)) {
                        throw new AppException(ErrorCode.BAD_REQUEST,
                                "Idempotency-Key đã được sử dụng với payload khác");
                    }
                    return receiptMapper.toResponse(existing);
                });
    }

    private String normalizeIdempotencyKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String key = raw.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Idempotency-Key không được vượt quá " + MAX_IDEMPOTENCY_KEY_LENGTH + " ký tự");
        }
        return key;
    }

    private UUID resolveLineLocationId(CreateInboundReceiptRequest request, ReceiveLineRequest line) {
        UUID locationId = line.locationId() != null ? line.locationId() : request.locationId();
        if (locationId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Moi dong nhan hang phai co locationId hoac phieu phai co locationId fallback");
        }
        return locationId;
    }

    private void assertLocationInWarehouse(UUID locationId, UUID warehouseId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay vi tri nhan hang"));
        if (location.getWarehouse() == null || !warehouseId.equals(location.getWarehouse().getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vi tri nhan hang khong thuoc kho cua don nhap");
        }
        if (!isInboundStorageLocation(location)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Vi tri nhan hang phai dang hoat dong, AVAILABLE va khong phai vi tri RMA");
        }
    }

    private void assertWarehouseVisible(UUID warehouseId, Collection<UUID> visibleWarehouseIds) {
        if (visibleWarehouseIds != null
                && (visibleWarehouseIds.isEmpty() || !visibleWarehouseIds.contains(warehouseId))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Ban khong duoc thao tac nhap hang cua kho nay");
        }
    }

    private boolean isInboundStorageLocation(Location location) {
        if (location == null || !Boolean.TRUE.equals(location.getIsActive())) {
            return false;
        }
        String status = StringUtils.hasText(location.getStatus())
                ? location.getStatus().trim().toUpperCase(Locale.ROOT)
                : "AVAILABLE";
        if (Set.of("BLOCKED", "MAINTENANCE", "INACTIVE", "DISABLED").contains(status)) {
            return false;
        }
        String type = normalizeLocationType(location.getLocationType());
        return !type.startsWith("RMA_") && !"STAGING".equals(type);
    }

    private InboundLocationSuggestionResponse toSuggestion(Location location, boolean existingProductLocation,
            boolean emptyLocation, Integer qtyOnHand) {
        return new InboundLocationSuggestionResponse(
                location.getId(),
                location.getCode(),
                location.getLocationType(),
                location.getZone(),
                existingProductLocation,
                emptyLocation,
                qtyOnHand);
    }

    private int safeQty(Integer qty) {
        return qty == null ? 0 : qty;
    }

    private String normalizeLocationType(String locationType) {
        return StringUtils.hasText(locationType) ? locationType.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeLot(String lotNumber) {
        return lotNumber == null ? "" : lotNumber.trim();
    }

    private String requestHash(CreateInboundReceiptRequest request) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("purchaseOrderId", request.purchaseOrderId());
        canonical.put("locationId", request.locationId());
        canonical.put("note", request.note() == null ? "" : request.note().trim());
        List<Map<String, Object>> lines = request.items().stream()
                .map(line -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("poItemId", line.poItemId());
                    value.put("receivedQty", line.receivedQty());
                    value.put("locationId", line.locationId());
                    value.put("note", line.note() == null ? "" : line.note().trim());
                    return value;
                })
                .sorted(Comparator
                        .comparing((Map<String, Object> line) -> String.valueOf(line.get("poItemId")))
                        .thenComparing(line -> String.valueOf(line.get("receivedQty")))
                        .thenComparing(line -> String.valueOf(line.get("note"))))
                .toList();
        canonical.put("items", lines);
        try {
            byte[] json = objectMapper.writeValueAsBytes(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể tạo idempotency hash");
        }
    }

    // Đồng bộ trạng thái đơn nhập theo tổng tiến độ nhận hàng.
    private void refreshPoStatus(UUID purchaseOrderId) {
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập"));
        List<PoItem> lines = poItemRepository.findByPurchaseOrderId(purchaseOrderId);

        boolean allReceived = lines.stream()
                .allMatch(l -> Objects.equals(l.getOrderedQty(), l.getReceivedQty()));

        boolean anyReceived = lines.stream()
                .anyMatch(l -> l.getReceivedQty() != null && l.getReceivedQty() > 0);

        if (allReceived) {
            po.setStatus(PurchaseOrderStatus.COMPLETED);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrderStatus.PARTIAL);
        } else {
            po.setStatus(PurchaseOrderStatus.APPROVED);
        }
        purchaseOrderRepository.save(po);
    }

    // Sinh mã phiếu nhập duy nhất.
    private String generateUniqueReceiptNumber() {
        for (int i = 0; i < 10; i++) {
            String candidate = CodeGenerator.generate("GRN");
            if (!receiptRepository.existsByReceiptNumber(candidate)) {
                return candidate;
            }
        }
        throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                "Không thể sinh mã phiếu nhập duy nhất, vui lòng thử lại");
    }
}
