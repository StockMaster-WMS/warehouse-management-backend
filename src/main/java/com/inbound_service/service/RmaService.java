package com.inbound_service.service;

import com.common.api.stock.StockAdjustCommand;
import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.notification.CreateNotificationCommand;
import com.common.notification.NotificationService;
import com.common.notification.NotificationSeverity;
import com.common.notification.NotificationType;
import com.common.util.CodeGenerator;
import com.inbound_service.dto.request.CreateRmaRequest;
import com.inbound_service.dto.request.DispositionRmaItemRequest;
import com.inbound_service.dto.request.ReceiveRmaRequest;
import com.inbound_service.dto.response.RmaReportResponse;
import com.inbound_service.dto.response.RmaResponse;
import com.inbound_service.entity.Rma;
import com.inbound_service.entity.RmaItem;
import com.inbound_service.repository.RmaRepository;
import com.outbound_service.entity.Customer;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.repository.CustomerRepository;
import com.outbound_service.repository.SalesOrderRepository;
import com.product_service.dto.response.ProductSummaryResponse;
import com.product_service.entity.Supplier;
import com.product_service.repository.SupplierRepository;
import com.product_service.service.ProductService;
import com.warehouse_service.dto.response.LocationResponse;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.WarehouseRepository;
import com.warehouse_service.service.StockLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaService {

    private final RmaRepository rmaRepository;
    private final StockLevelService stockLevelService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final ProductService productService;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final SalesOrderRepository salesOrderRepository;

    private static final Set<String> RMA_LOCATION_TYPES = Set.of(
            "RMA_DAMAGED", "RMA_EXPIRED", "RMA_QUARANTINE", "RMA_RESTOCK");

    public List<RmaResponse> getAll(String keyword, String status, String reason, UUID warehouseId,
            OffsetDateTime createdFrom, OffsetDateTime createdTo) {
        return getAll(keyword, status, reason, warehouseId, createdFrom, createdTo, null);
    }

    public List<RmaResponse> getAll(String keyword, String status, String reason, UUID warehouseId,
            OffsetDateTime createdFrom, OffsetDateTime createdTo, Collection<UUID> visibleWarehouseIds) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : null;
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim().toUpperCase(Locale.ROOT) : null;

        return rmaRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(rma -> visibleWarehouseIds == null || visibleWarehouseIds.contains(rma.getWarehouseId()))
                .filter(rma -> normalizedKeyword == null
                        || containsIgnoreCase(rma.getRmaNumber(), normalizedKeyword)
                        || containsIgnoreCase(rma.getCustomerName(), normalizedKeyword)
                        || containsIgnoreCase(rma.getSupplierName(), normalizedKeyword)
                        || containsIgnoreCase(rma.getReason(), normalizedKeyword))
                .filter(rma -> normalizedStatus == null
                        || (rma.getStatus() != null && rma.getStatus().name().equals(normalizedStatus)))
                .filter(rma -> normalizedReason == null
                        || (rma.getReason() != null && rma.getReason().trim().toUpperCase(Locale.ROOT).equals(normalizedReason)))
                .filter(rma -> warehouseId == null || warehouseId.equals(rma.getWarehouseId()))
                .filter(rma -> createdFrom == null
                        || (rma.getCreatedAt() != null && !rma.getCreatedAt().isBefore(createdFrom)))
                .filter(rma -> createdTo == null
                        || (rma.getCreatedAt() != null && !rma.getCreatedAt().isAfter(createdTo)))
                .map(this::toResponse)
                .toList();
    }

    public RmaResponse getById(UUID id) {
        return getById(id, null);
    }

    public RmaResponse getById(UUID id, Collection<UUID> visibleWarehouseIds) {
        Rma rma = rmaRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu trả hàng"));
        assertVisible(rma, visibleWarehouseIds);
        return toResponse(rma);
    }

    public List<LocationResponse> getReturnLocations(UUID warehouseId, String condition,
            Collection<UUID> visibleWarehouseIds) {
        if (warehouseId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "warehouseId là bắt buộc");
        }
        if (visibleWarehouseIds != null && !visibleWarehouseIds.contains(warehouseId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được thao tác kho này");
        }
        String preferredType = expectedReturnLocationType(normalizeCondition(condition));
        return locationRepository.findByWarehouseId(warehouseId).stream()
                .filter(location -> Boolean.TRUE.equals(location.getIsActive()))
                .filter(location -> location.getStatus() == null || "AVAILABLE".equalsIgnoreCase(location.getStatus()))
                .filter(location -> {
                    String type = normalizeLocationType(location.getLocationType());
                    return preferredType.equals(type)
                            || ("RMA_QUARANTINE".equals(preferredType) && RMA_LOCATION_TYPES.contains(type));
                })
                .sorted((left, right) -> {
                    int leftRank = preferredType.equals(normalizeLocationType(left.getLocationType())) ? 0 : 1;
                    int rightRank = preferredType.equals(normalizeLocationType(right.getLocationType())) ? 0 : 1;
                    if (leftRank != rightRank) {
                        return Integer.compare(leftRank, rightRank);
                    }
                    return String.CASE_INSENSITIVE_ORDER.compare(left.getCode(), right.getCode());
                })
                .map(this::toLocationResponse)
                .toList();
    }

    @Transactional
    public RmaResponse create(CreateRmaRequest request) {
        return create(request, null);
    }

    @Transactional
    public RmaResponse create(CreateRmaRequest request, UUID creatorId) {
        return createInternal(request, creatorId, normalizeReturnType(request.returnType()));
    }

    @Transactional
    public RmaResponse createCustomerReturn(CreateRmaRequest request, UUID creatorId) {
        return createInternal(request, creatorId, "CUSTOMER");
    }

    @Transactional
    public RmaResponse createSupplierReturn(CreateRmaRequest request, UUID creatorId) {
        return createInternal(request, creatorId, "SUPPLIER");
    }

    private RmaResponse createInternal(CreateRmaRequest request, UUID creatorId, String returnType) {
        validateCreateRequest(request);
        warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho nhận hàng trả"));
        Supplier supplier = resolveSupplier(returnType, request);
        SalesOrder salesOrder = resolveCustomerSalesOrder(returnType, request);
        Customer customer = resolveCustomer(returnType, request, salesOrder);
        validateCustomerReturnItems(returnType, request, salesOrder);

        Rma rma = Rma.builder()
                .rmaNumber(CodeGenerator.generate("RMA"))
                .returnType(returnType)
                .salesOrderId(salesOrder == null ? request.salesOrderId() : salesOrder.getId())
                .supplierId(supplier == null ? request.supplierId() : supplier.getId())
                .supplierName(supplier == null ? request.supplierName() : supplier.getName())
                .customerId(customer == null ? request.customerId() : customer.getId())
                .customerName(resolveCustomerName(request, salesOrder, customer))
                .warehouseId(salesOrder == null ? request.warehouseId() : salesOrder.getWarehouseId())
                .reason(request.reason())
                .status(Rma.RmaStatus.REQUESTED)
                .createdBy(creatorId)
                .build();

        List<RmaItem> items = request.items().stream().map(req -> RmaItem.builder()
                .rma(rma)
                .productId(req.productId())
                .salesOrderItemId(req.salesOrderItemId())
                .expectedQty(req.expectedQty())
                .lotNumber(normalizeLot(req.lotNumber()))
                .returnLocationId(req.locationId())
                .build()).toList();

        if ("SUPPLIER".equals(returnType)) {
            for (RmaItem item : items) {
                if (item.getReturnLocationId() == null) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Phiếu trả NCC cần chọn vị trí xuất trả cho từng dòng");
                }
                assertLocationInWarehouse(item.getReturnLocationId(), request.warehouseId());
            }
        }

        rma.setItems(items);
        Rma saved = rmaRepository.save(rma);
        RmaResponse response = toResponse(saved);
        auditLogService.record("RMA", "CREATE", "Tạo yêu cầu hàng trả",
                "RMA", saved.getId(), displayRma(saved), null, response, null, rmaMetadata(saved));
        notifyCreated(saved);
        return response;
    }

    @Transactional
    public RmaResponse receiveItem(UUID rmaId, ReceiveRmaRequest request) {
        return receiveItem(rmaId, request, null, null);
    }

    @Transactional
    public RmaResponse receiveItem(UUID rmaId, ReceiveRmaRequest request, UUID receiverId,
            Collection<UUID> visibleWarehouseIds) {
        Rma rma = rmaRepository.findByIdWithItemsForUpdate(rmaId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy RMA"));
        assertVisible(rma, visibleWarehouseIds);

        RmaItem item = rma.getItems().stream()
                .filter(i -> i.getId().equals(request.itemId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy mặt hàng trong RMA"));

        if (StringUtils.hasText(item.getDispositionAction())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Dòng hàng trả đã được xử lý sau kiểm định, không thể sửa nhận hàng");
        }

        if (rma.getStatus() == Rma.RmaStatus.COMPLETED || rma.getStatus() == Rma.RmaStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể nhận hàng cho RMA đã kết thúc");
        }
        if (!isSupplierReturn(rma)
                && rma.getStatus() != Rma.RmaStatus.APPROVED
                && rma.getStatus() != Rma.RmaStatus.RECEIVED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phiếu khách trả phải được duyệt trước khi nhận hàng");
        }

        int expectedQty = safeQty(item.getExpectedQty());
        int oldReceivedQty = safeQty(item.getReceivedQty());
        int newReceivedQty = request.receivedQty();
        if (newReceivedQty > expectedQty) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Số lượng nhận không được vượt quá số lượng dự kiến (" + newReceivedQty + "/" + expectedQty + ")");
        }

        UUID oldLocationId = item.getReceivedLocationId();
        UUID newLocationId = request.locationId() != null ? request.locationId() : oldLocationId;
        if (newReceivedQty > 0 && newLocationId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cần chọn vị trí nhận hàng trả lại");
        }
        String normalizedCondition = normalizeCondition(request.condition());
        if (newReceivedQty > 0) {
            assertReturnReceiveLocation(newLocationId, rma.getWarehouseId(), normalizedCondition);
        }

        RmaResponse before = toResponse(rma);
        replaceRestockedQuantity(rma, item, oldReceivedQty, oldLocationId, newReceivedQty, newLocationId);

        item.setReceivedQty(newReceivedQty);
        item.setReceivedLocationId(newReceivedQty > 0 ? newLocationId : null);
        item.setCondition(normalizedCondition);
        item.setNotes(request.notes());

        if (hasAnyReceivedItem(rma)) {
            rma.setStatus(Rma.RmaStatus.RECEIVED);
            rma.setReceivedBy(receiverId);
            rma.setReceivedAt(OffsetDateTime.now());
        } else {
            rma.setStatus(rma.getApprovedAt() == null ? Rma.RmaStatus.REQUESTED : Rma.RmaStatus.APPROVED);
            rma.setReceivedBy(null);
            rma.setReceivedAt(null);
        }

        Rma saved = rmaRepository.save(rma);
        RmaResponse after = toResponse(saved);
        auditLogService.record("RMA", "RECEIVE", "Ghi nhận nhận hàng trả",
                "RMA", saved.getId(), displayRma(saved), before, after, null, rmaMetadata(saved));
        if (newReceivedQty > oldReceivedQty) {
            notifyReceived(saved, newReceivedQty, expectedQty);
        }
        return after;
    }

    @Transactional
    public RmaResponse complete(UUID id) {
        return complete(id, null, null);
    }

    @Transactional
    public RmaResponse dispositionItem(UUID rmaId, UUID itemId, DispositionRmaItemRequest request,
            UUID actorId, Collection<UUID> visibleWarehouseIds) {
        Rma rma = rmaRepository.findByIdWithItemsForUpdate(rmaId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy RMA"));
        assertVisible(rma, visibleWarehouseIds);
        if (isSupplierReturn(rma)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phiếu trả NCC không dùng bước xử lý sau kiểm định hàng khách trả");
        }
        if (rma.getStatus() == Rma.RmaStatus.CANCELLED || rma.getStatus() == Rma.RmaStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể xử lý RMA đã kết thúc");
        }

        RmaItem item = rma.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng hàng trả"));
        if (safeQty(item.getReceivedQty()) <= 0 || item.getReceivedLocationId() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Dòng hàng trả chưa được nhận vào vị trí RMA");
        }
        if (StringUtils.hasText(item.getDispositionAction())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Dòng hàng trả đã được xử lý sau kiểm định");
        }

        String action = normalizeDispositionAction(request.action());
        RmaResponse before = toResponse(rma);
        UUID supplierReturnRmaId = null;
        if ("RETURN_TO_SUPPLIER".equals(action)) {
            supplierReturnRmaId = createLinkedSupplierReturn(rma, item, request.supplierId(), actorId, request.note());
        }
        applyDispositionStock(rma, item, action, request.targetLocationId());

        item.setDispositionAction(action);
        item.setDispositionLocationId("RESTOCK".equals(action) ? request.targetLocationId() : null);
        item.setDispositionAt(OffsetDateTime.now());
        item.setDispositionBy(actorId);
        item.setDispositionNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
        item.setSupplierReturnRmaId(supplierReturnRmaId);

        Rma saved = rmaRepository.save(rma);
        RmaResponse after = toResponse(saved);
        auditLogService.record("RMA", "DISPOSITION", "Xử lý sau kiểm định hàng trả: " + action,
                "RMA", saved.getId(), displayRma(saved), before, after, request.note(), rmaMetadata(saved));
        return after;
    }

    @Transactional
    public RmaResponse complete(UUID id, UUID completedBy, Collection<UUID> visibleWarehouseIds) {
        Rma rma = rmaRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy RMA"));
        assertVisible(rma, visibleWarehouseIds);

        if (rma.getStatus() == Rma.RmaStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "RMA đã hoàn tất trước đó");
        }
        if (rma.getStatus() == Rma.RmaStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể hoàn tất RMA đã hủy");
        }
        for (RmaItem item : rma.getItems()) {
            if (isSupplierReturn(rma)) {
                continue;
            }
            int expected = safeQty(item.getExpectedQty());
            int received = safeQty(item.getReceivedQty());
            if (received != expected) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Chưa nhận đủ hàng trả cho sản phẩm " + item.getProductId()
                                + " (" + received + "/" + expected + ")");
            }
        }

        RmaResponse before = toResponse(rma);
        for (RmaItem item : rma.getItems()) {
            if (!isSupplierReturn(rma) && safeQty(item.getReceivedQty()) > 0
                    && !StringUtils.hasText(item.getDispositionAction())) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Dòng hàng trả " + item.getId() + " chưa được xử lý sau kiểm định");
            }
        }
        rma.setStatus(Rma.RmaStatus.COMPLETED);
        rma.setCompletedAt(OffsetDateTime.now());
        rma.setCompletedBy(completedBy);
        Rma saved = rmaRepository.save(rma);
        RmaResponse after = toResponse(saved);
        auditLogService.record("RMA", "COMPLETE", "Hoàn tất hàng trả",
                "RMA", saved.getId(), displayRma(saved), before, after, null, rmaMetadata(saved));
        return after;
    }

    @Transactional
    public RmaResponse approve(UUID id, UUID approvedBy, Collection<UUID> visibleWarehouseIds) {
        Rma rma = rmaRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy RMA"));
        assertVisible(rma, visibleWarehouseIds);
        if (rma.getStatus() == Rma.RmaStatus.APPROVED || rma.getStatus() == Rma.RmaStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phiếu trả hàng đã được duyệt trước đó");
        }
        if (rma.getStatus() == Rma.RmaStatus.REJECTED || rma.getStatus() == Rma.RmaStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể duyệt phiếu trả hàng đã từ chối hoặc đã hủy");
        }

        RmaResponse before = toResponse(rma);
        if (isSupplierReturn(rma)) {
            approveSupplierReturn(rma);
        } else {
            approveCustomerReturn(rma);
        }

        rma.setStatus(Rma.RmaStatus.APPROVED);
        rma.setApprovedBy(approvedBy);
        rma.setApprovedAt(OffsetDateTime.now());
        Rma saved = rmaRepository.save(rma);
        RmaResponse after = toResponse(saved);
        auditLogService.record("RMA", "APPROVE", "Duyệt phiếu trả hàng",
                "RMA", saved.getId(), displayRma(saved), before, after, null, rmaMetadata(saved));
        return after;
    }

    @Transactional
    public RmaResponse reject(UUID id, String reason, UUID rejectedBy, Collection<UUID> visibleWarehouseIds) {
        Rma rma = rmaRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy RMA"));
        assertVisible(rma, visibleWarehouseIds);
        if (rma.getStatus() == Rma.RmaStatus.APPROVED || rma.getStatus() == Rma.RmaStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể từ chối phiếu trả hàng đã duyệt hoặc đã hoàn tất");
        }
        if (rma.getStatus() == Rma.RmaStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể từ chối phiếu trả hàng đã hủy");
        }
        if (hasAnyReceivedItem(rma)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không thể từ chối phiếu khách trả đã nhận hàng. Nếu nhận sai, hãy chỉnh số lượng nhận về 0 trước.");
        }

        RmaResponse before = toResponse(rma);
        rma.setStatus(Rma.RmaStatus.REJECTED);
        rma.setRejectedBy(rejectedBy);
        rma.setRejectedAt(OffsetDateTime.now());
        rma.setRejectionReason(reason == null ? null : reason.trim());
        Rma saved = rmaRepository.save(rma);
        RmaResponse after = toResponse(saved);
        auditLogService.record("RMA", "REJECT", "Từ chối phiếu trả hàng",
                "RMA", saved.getId(), displayRma(saved), before, after, reason, rmaMetadata(saved));
        return after;
    }

    @Transactional
    public RmaResponse cancel(UUID id, String reason, UUID cancelledBy, Collection<UUID> visibleWarehouseIds) {
        Rma rma = rmaRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy RMA"));
        assertVisible(rma, visibleWarehouseIds);
        if (rma.getStatus() == Rma.RmaStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể hủy RMA đã hoàn tất");
        }
        if (rma.getStatus() == Rma.RmaStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "RMA đã hủy trước đó");
        }
        if (hasAnyReceivedItem(rma)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không thể hủy RMA đã nhận hàng. Nếu nhận sai, hãy chỉnh số lượng nhận về 0 trước.");
        }

        RmaResponse before = toResponse(rma);
        rma.setStatus(Rma.RmaStatus.CANCELLED);
        rma.setCancelledBy(cancelledBy);
        rma.setCancelledAt(OffsetDateTime.now());
        rma.setCancelReason(StringUtils.hasText(reason) ? reason.trim() : null);
        Rma saved = rmaRepository.save(rma);
        RmaResponse after = toResponse(saved);
        auditLogService.record("RMA", "CANCEL", "Hủy yêu cầu hàng trả",
                "RMA", saved.getId(), displayRma(saved), before, after, reason, rmaMetadata(saved));
        return after;
    }

    public RmaReportResponse getReport(UUID warehouseId, String returnType,
            OffsetDateTime createdFrom, OffsetDateTime createdTo, Collection<UUID> visibleWarehouseIds) {
        String normalizedType = StringUtils.hasText(returnType) ? normalizeReturnType(returnType) : null;
        List<Rma> rows = rmaRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(rma -> visibleWarehouseIds == null || visibleWarehouseIds.contains(rma.getWarehouseId()))
                .filter(rma -> warehouseId == null || warehouseId.equals(rma.getWarehouseId()))
                .filter(rma -> normalizedType == null || normalizedType.equals(normalizeReturnType(rma.getReturnType())))
                .filter(rma -> createdFrom == null
                        || (rma.getCreatedAt() != null && !rma.getCreatedAt().isBefore(createdFrom)))
                .filter(rma -> createdTo == null
                        || (rma.getCreatedAt() != null && !rma.getCreatedAt().isAfter(createdTo)))
                .toList();

        int totalExpected = rows.stream().flatMap(rma -> rma.getItems().stream()).mapToInt(i -> safeQty(i.getExpectedQty())).sum();
        int totalReceived = rows.stream().flatMap(rma -> rma.getItems().stream()).mapToInt(i -> safeQty(i.getReceivedQty())).sum();
        int supplierReturned = rows.stream()
                .filter(this::isSupplierReturn)
                .flatMap(rma -> rma.getItems().stream())
                .mapToInt(i -> safeQty(i.getExpectedQty()))
                .sum();

        return new RmaReportResponse(
                rows.size(),
                rows.stream().filter(rma -> !isSupplierReturn(rma)).count(),
                rows.stream().filter(this::isSupplierReturn).count(),
                rows.stream().filter(rma -> rma.getStatus() == Rma.RmaStatus.REQUESTED || rma.getStatus() == Rma.RmaStatus.RECEIVED).count(),
                rows.stream().filter(rma -> rma.getStatus() == Rma.RmaStatus.APPROVED).count(),
                rows.stream().filter(rma -> rma.getStatus() == Rma.RmaStatus.REJECTED).count(),
                rows.stream().filter(rma -> rma.getStatus() == Rma.RmaStatus.COMPLETED).count(),
                totalExpected,
                totalReceived,
                supplierReturned,
                groupSupplierStats(rows),
                groupReasonStats(rows)
        );
    }

    private RmaResponse toResponse(Rma rma) {
        Map<UUID, ProductSummaryResponse> productMap = loadProducts(rma);
        Map<UUID, Location> locationMap = loadLocations(rma);
        String warehouseName = rma.getWarehouseId() == null ? null : warehouseRepository.findById(rma.getWarehouseId())
                .map(Warehouse::getName)
                .orElse(null);

        List<RmaResponse.ItemResponse> items = rma.getItems().stream()
                .map(i -> {
                    ProductSummaryResponse product = productMap.get(i.getProductId());
                    Location receivedLocation = i.getReceivedLocationId() == null ? null : locationMap.get(i.getReceivedLocationId());
                    Location returnLocation = i.getReturnLocationId() == null ? null : locationMap.get(i.getReturnLocationId());
                    Location dispositionLocation = i.getDispositionLocationId() == null ? null : locationMap.get(i.getDispositionLocationId());
                    int expected = safeQty(i.getExpectedQty());
                    int received = safeQty(i.getReceivedQty());
                    return new RmaResponse.ItemResponse(
                            i.getId(),
                            i.getProductId(),
                            i.getSalesOrderItemId(),
                            product == null ? null : product.name(),
                            product == null ? null : product.sku(),
                            expected,
                            received,
                            Math.max(expected - received, 0),
                            i.getReceivedLocationId(),
                            receivedLocation == null ? null : receivedLocation.getCode(),
                            i.getReturnLocationId(),
                            returnLocation == null ? null : returnLocation.getCode(),
                            i.getLotNumber(),
                            i.getCondition(),
                            i.getDispositionAction(),
                            i.getDispositionLocationId(),
                            dispositionLocation == null ? null : dispositionLocation.getCode(),
                            i.getDispositionAt(),
                            i.getDispositionBy(),
                            i.getDispositionNote(),
                            i.getSupplierReturnRmaId(),
                            i.getNotes()
                    );
                }).toList();

        int totalExpected = items.stream().mapToInt(RmaResponse.ItemResponse::expectedQty).sum();
        int totalReceived = items.stream().mapToInt(RmaResponse.ItemResponse::receivedQty).sum();

        return new RmaResponse(
                rma.getId(),
                rma.getRmaNumber(),
                normalizeReturnType(rma.getReturnType()),
                rma.getSalesOrderId(),
                rma.getSupplierId(),
                rma.getSupplierName(),
                rma.getCustomerId(),
                rma.getCustomerName(),
                rma.getStatus().name(),
                rma.getReason(),
                rma.getWarehouseId(),
                warehouseName,
                rma.getCreatedAt(),
                rma.getReceivedAt(),
                rma.getCompletedAt(),
                rma.getCreatedBy(),
                rma.getReceivedBy(),
                rma.getCompletedBy(),
                rma.getApprovedBy(),
                rma.getApprovedAt(),
                rma.getRejectedBy(),
                rma.getRejectedAt(),
                rma.getRejectionReason(),
                rma.getCancelledBy(),
                rma.getCancelledAt(),
                rma.getCancelReason(),
                totalExpected,
                totalReceived,
                Math.max(totalExpected - totalReceived, 0),
                items
        );
    }

    private void validateCreateRequest(CreateRmaRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "RMA phải có ít nhất một dòng hàng trả");
        }
        for (CreateRmaRequest.ItemRequest item : request.items()) {
            if (item.expectedQty() == null || item.expectedQty() <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng dự kiến trả phải lớn hơn 0");
            }
        }
    }

    private SalesOrder resolveCustomerSalesOrder(String returnType, CreateRmaRequest request) {
        if (!"CUSTOMER".equals(returnType)) {
            return null;
        }
        if (request.salesOrderId() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phiếu khách trả phải chọn đơn xuất liên quan");
        }
        SalesOrder salesOrder = salesOrderRepository.findWithItemsById(request.salesOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất liên quan"));
        if (salesOrder.getStatus() != SalesOrderStatus.SHIPPED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ tạo phiếu khách trả từ đơn xuất đã giao");
        }
        if (!salesOrder.getWarehouseId().equals(request.warehouseId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Kho nhận hàng trả phải trùng với kho xuất của đơn đã chọn");
        }
        return salesOrder;
    }

    private Customer resolveCustomer(String returnType, CreateRmaRequest request, SalesOrder salesOrder) {
        if (!"CUSTOMER".equals(returnType)) {
            return null;
        }
        UUID customerId = request.customerId() != null ? request.customerId()
                : salesOrder == null ? null : salesOrder.getCustomerId();
        if (customerId == null) {
            return null;
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khách hàng"));
        if (salesOrder != null && salesOrder.getCustomerId() != null && !salesOrder.getCustomerId().equals(customer.getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Khách hàng không khớp với đơn xuất đã chọn");
        }
        if (salesOrder != null && salesOrder.getCustomerId() == null
                && StringUtils.hasText(salesOrder.getCustomerName())
                && !salesOrder.getCustomerName().trim().equalsIgnoreCase(customer.getName().trim())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Khách hàng không khớp với đơn xuất đã chọn");
        }
        return customer;
    }

    private String resolveCustomerName(CreateRmaRequest request, SalesOrder salesOrder, Customer customer) {
        if (customer != null) {
            return customer.getName();
        }
        if (salesOrder != null && StringUtils.hasText(salesOrder.getCustomerName())) {
            return salesOrder.getCustomerName().trim();
        }
        return StringUtils.hasText(request.customerName()) ? request.customerName().trim() : null;
    }

    private void validateCustomerReturnItems(String returnType, CreateRmaRequest request, SalesOrder salesOrder) {
        if (!"CUSTOMER".equals(returnType)) {
            return;
        }
        if (salesOrder == null || salesOrder.getItems() == null || salesOrder.getItems().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn xuất đã chọn chưa có dòng hàng đã xuất");
        }

        Map<UUID, SalesOrderItem> orderItemById = salesOrder.getItems().stream()
                .collect(Collectors.toMap(SalesOrderItem::getId, Function.identity()));
        Map<UUID, Integer> shippedByProduct = salesOrder.getItems().stream()
                .collect(Collectors.groupingBy(
                        SalesOrderItem::getProductId,
                        Collectors.summingInt(item -> safeQty(item.getShippedQty()))));
        Map<UUID, Integer> requestedByProduct = new LinkedHashMap<>();

        for (CreateRmaRequest.ItemRequest item : request.items()) {
            if (item.salesOrderItemId() != null) {
                SalesOrderItem sourceLine = orderItemById.get(item.salesOrderItemId());
                if (sourceLine == null) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Dòng đơn xuất không thuộc đơn xuất đã chọn");
                }
                if (!sourceLine.getProductId().equals(item.productId())) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Sản phẩm trả không khớp với dòng đơn xuất đã chọn");
                }
            }
            if (shippedByProduct.containsKey(item.productId())) {
                requestedByProduct.merge(item.productId(), safeQty(item.expectedQty()), Integer::sum);
            }
        }

        Map<UUID, Integer> alreadyReturnedByProduct = customerReturnedQtyByProduct(salesOrder.getId());
        for (Map.Entry<UUID, Integer> entry : requestedByProduct.entrySet()) {
            UUID productId = entry.getKey();
            int shipped = shippedByProduct.getOrDefault(productId, 0);
            int alreadyReturned = alreadyReturnedByProduct.getOrDefault(productId, 0);
            int requested = entry.getValue();
            if (alreadyReturned + requested > shipped) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Số lượng trả vượt số đã xuất cho sản phẩm " + productId
                                + " (đã xuất " + shipped
                                + ", đã tạo trả " + alreadyReturned
                                + ", đang trả " + requested + ")");
            }
        }
    }

    private Map<UUID, Integer> customerReturnedQtyByProduct(UUID salesOrderId) {
        return rmaRepository.findBySalesOrderIdWithItems(salesOrderId).stream()
                .filter(rma -> "CUSTOMER".equals(normalizeReturnType(rma.getReturnType())))
                .filter(rma -> rma.getStatus() != Rma.RmaStatus.REJECTED && rma.getStatus() != Rma.RmaStatus.CANCELLED)
                .flatMap(rma -> rma.getItems().stream())
                .collect(Collectors.groupingBy(
                        RmaItem::getProductId,
                        Collectors.summingInt(item -> safeQty(item.getExpectedQty()))));
    }

    private void replaceRestockedQuantity(Rma rma, RmaItem item,
            int oldReceivedQty, UUID oldLocationId,
            int newReceivedQty, UUID newLocationId) {
        if (oldReceivedQty == newReceivedQty && Objects.equals(oldLocationId, newLocationId)) {
            return;
        }

        if (oldReceivedQty > 0 && oldLocationId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "RMA thiếu vị trí nhận cũ, không thể điều chỉnh tồn kho an toàn");
        }

        if (oldReceivedQty > 0) {
            stockLevelService.adjust(new StockAdjustCommand(
                    rma.getWarehouseId(),
                    oldLocationId,
                    item.getProductId(),
                    normalizeLot(item.getLotNumber()),
                    -oldReceivedQty,
                    "RMA_RECEIVE:" + rma.getId() + ":" + item.getId() + ":REVERSE:"
                            + oldLocationId + ":" + oldReceivedQty + ":TO:" + newReceivedQty + ":" + newLocationId,
                    "RMA",
                    rma.getId()
            ));
        }

        if (newReceivedQty > 0) {
            stockLevelService.adjust(new StockAdjustCommand(
                    rma.getWarehouseId(),
                    newLocationId,
                    item.getProductId(),
                    normalizeLot(item.getLotNumber()),
                    newReceivedQty,
                    "RMA_RECEIVE:" + rma.getId() + ":" + item.getId() + ":APPLY:"
                            + newLocationId + ":" + newReceivedQty + ":FROM:" + oldReceivedQty + ":" + oldLocationId,
                    "RMA",
                    rma.getId()
            ));
        }
    }

    private void applyDispositionStock(Rma rma, RmaItem item, String action, UUID targetLocationId) {
        int receivedQty = safeQty(item.getReceivedQty());
        UUID rmaLocationId = item.getReceivedLocationId();
        if ("KEEP_QUARANTINE".equals(action)) {
            return;
        }
        if ("RESTOCK".equals(action)) {
            if (targetLocationId == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "RESTOCK cần chọn vị trí nhập lại tồn bán được");
            }
            assertSellableRestockLocation(targetLocationId, rma.getWarehouseId());
            moveRmaStock(rma, item, rmaLocationId, targetLocationId, receivedQty, "RESTOCK");
            return;
        }
        if ("RETURN_TO_SUPPLIER".equals(action)) {
            return;
        }
        if ("SCRAP".equals(action)) {
            stockLevelService.adjust(new StockAdjustCommand(
                    rma.getWarehouseId(),
                    rmaLocationId,
                    item.getProductId(),
                    normalizeLot(item.getLotNumber()),
                    -receivedQty,
                    "RMA_DISPOSITION:" + rma.getId() + ":" + item.getId() + ":" + action,
                    "RMA",
                    rma.getId()
            ));
            return;
        }
        throw new AppException(ErrorCode.BAD_REQUEST, "Hành động xử lý RMA không hợp lệ: " + action);
    }

    private UUID createLinkedSupplierReturn(Rma customerRma, RmaItem sourceItem, UUID supplierId,
            UUID actorId, String note) {
        if (supplierId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "RETURN_TO_SUPPLIER can chon supplierId de tao phieu tra nha cung cap");
        }
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay nha cung cap"));

        Rma supplierRma = Rma.builder()
                .rmaNumber(CodeGenerator.generate("RMA"))
                .returnType("SUPPLIER")
                .supplierId(supplier.getId())
                .supplierName(supplier.getName())
                .warehouseId(customerRma.getWarehouseId())
                .reason(StringUtils.hasText(note)
                        ? note.trim()
                        : "Tao tu xu ly hang khach tra " + customerRma.getRmaNumber())
                .status(Rma.RmaStatus.REQUESTED)
                .createdBy(actorId)
                .build();

        RmaItem supplierItem = RmaItem.builder()
                .rma(supplierRma)
                .productId(sourceItem.getProductId())
                .expectedQty(safeQty(sourceItem.getReceivedQty()))
                .lotNumber(normalizeLot(sourceItem.getLotNumber()))
                .returnLocationId(sourceItem.getReceivedLocationId())
                .notes("Tao tu RMA " + customerRma.getRmaNumber() + ", item " + sourceItem.getId())
                .build();
        supplierRma.setItems(List.of(supplierItem));

        Rma saved = rmaRepository.save(supplierRma);
        RmaResponse response = toResponse(saved);
        auditLogService.record("RMA", "CREATE", "Tao phieu tra NCC tu hang khach tra",
                "RMA", saved.getId(), displayRma(saved), null, response, note, rmaMetadata(saved));
        notifyCreated(saved);
        return saved.getId();
    }

    private void moveRmaStock(Rma rma, RmaItem item, UUID fromLocationId, UUID toLocationId, int qty, String action) {
        stockLevelService.adjust(new StockAdjustCommand(
                rma.getWarehouseId(),
                fromLocationId,
                item.getProductId(),
                normalizeLot(item.getLotNumber()),
                -qty,
                "RMA_DISPOSITION:" + rma.getId() + ":" + item.getId() + ":" + action + ":OUT",
                "RMA",
                rma.getId()
        ));
        stockLevelService.adjust(new StockAdjustCommand(
                rma.getWarehouseId(),
                toLocationId,
                item.getProductId(),
                normalizeLot(item.getLotNumber()),
                qty,
                "RMA_DISPOSITION:" + rma.getId() + ":" + item.getId() + ":" + action + ":IN",
                "RMA",
                rma.getId()
        ));
    }

    private String normalizeDispositionAction(String action) {
        if (!StringUtils.hasText(action)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Hành động xử lý là bắt buộc");
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RESTOCK", "PUT_BACK", "SELLABLE" -> "RESTOCK";
            case "KEEP_QUARANTINE", "QUARANTINE", "HOLD" -> "KEEP_QUARANTINE";
            case "SCRAP", "DISPOSE", "DESTROY" -> "SCRAP";
            case "RETURN_TO_SUPPLIER", "SUPPLIER_RETURN", "RETURN_SUPPLIER" -> "RETURN_TO_SUPPLIER";
            default -> throw new AppException(ErrorCode.BAD_REQUEST, "Hành động xử lý RMA không hợp lệ: " + action);
        };
    }

    private void assertSellableRestockLocation(UUID locationId, UUID warehouseId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vị trí nhập lại tồn"));
        if (location.getWarehouse() == null || !warehouseId.equals(location.getWarehouse().getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vị trí nhập lại tồn không thuộc kho của RMA");
        }
        if (!Boolean.TRUE.equals(location.getIsActive())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vị trí nhập lại tồn đã bị vô hiệu hóa");
        }
        if (location.getStatus() != null && !"AVAILABLE".equalsIgnoreCase(location.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vị trí nhập lại tồn không ở trạng thái AVAILABLE");
        }
        String type = normalizeLocationType(location.getLocationType());
        if (RMA_LOCATION_TYPES.contains(type) || "STAGING".equals(type)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "RESTOCK phải chọn vị trí tồn bán được, không chọn vị trí RMA/STAGING");
        }
    }

    private void assertVisible(Rma rma, Collection<UUID> visibleWarehouseIds) {
        if (visibleWarehouseIds != null
                && (visibleWarehouseIds.isEmpty() || !visibleWarehouseIds.contains(rma.getWarehouseId()))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được thao tác RMA của kho này");
        }
    }

    private void assertLocationInWarehouse(UUID locationId, UUID warehouseId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vị trí nhận hàng trả"));
        if (location.getWarehouse() == null || !warehouseId.equals(location.getWarehouse().getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vị trí nhận hàng trả không thuộc kho của RMA");
        }
    }

    private void assertReturnReceiveLocation(UUID locationId, UUID warehouseId, String condition) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vị trí nhận hàng trả"));
        if (location.getWarehouse() == null || !warehouseId.equals(location.getWarehouse().getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vị trí nhận hàng trả không thuộc kho của RMA");
        }
        if (!Boolean.TRUE.equals(location.getIsActive())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vị trí nhận hàng trả đã bị vô hiệu hóa");
        }
        if (location.getStatus() != null && !"AVAILABLE".equalsIgnoreCase(location.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vị trí nhận hàng trả không ở trạng thái AVAILABLE");
        }
        String actualType = normalizeLocationType(location.getLocationType());
        String expectedType = expectedReturnLocationType(condition);
        if (!RMA_LOCATION_TYPES.contains(actualType)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Vị trí nhận hàng trả phải là vị trí RMA riêng, không được chọn vị trí thường");
        }
        if (!expectedType.equals(actualType) && !"RMA_QUARANTINE".equals(actualType)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Tình trạng " + (condition == null ? "chưa xác định" : condition)
                            + " phải nhận vào vị trí " + expectedType + " hoặc RMA_QUARANTINE");
        }
    }

    private String expectedReturnLocationType(String condition) {
        String normalized = condition == null ? "" : condition.trim().toUpperCase(Locale.ROOT);
        if (Set.of("EXPIRED", "EXPIRY", "HET_HAN", "HẾT_HẠN", "QUA_HAN", "OUT_OF_DATE").contains(normalized)) {
            return "RMA_EXPIRED";
        }
        if (Set.of("DAMAGED", "DEFECTIVE", "BROKEN", "FAULTY", "ERROR", "LOI", "LỖI", "HU_HONG", "HƯ_HỎNG").contains(normalized)) {
            return "RMA_DAMAGED";
        }
        if (Set.of("GOOD", "OK", "SELLABLE", "RESTOCK", "NEW", "NORMAL").contains(normalized)) {
            return "RMA_RESTOCK";
        }
        return "RMA_QUARANTINE";
    }

    private String normalizeLocationType(String locationType) {
        return StringUtils.hasText(locationType) ? locationType.trim().toUpperCase(Locale.ROOT) : "";
    }

    private LocationResponse toLocationResponse(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getWarehouse() == null ? null : location.getWarehouse().getId(),
                location.getCode(),
                location.getZone(),
                location.getAisle(),
                location.getRack(),
                location.getLevel(),
                location.getBin(),
                location.getLocationType(),
                location.getMaxWeightKg(),
                location.getMaxVolumeCm3(),
                location.getPickSequence(),
                location.getStatus(),
                location.getIsActive(),
                location.getIsColdZone(),
                location.getIsHazmatZone(),
                location.getIsHeavyZone(),
                location.getCreatedAt());
    }

    private void approveCustomerReturn(Rma rma) {
        if (rma.getSalesOrderId() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phiếu khách trả thiếu đơn xuất liên quan");
        }
    }

    private void approveSupplierReturn(Rma rma) {
        for (RmaItem item : rma.getItems()) {
            UUID locationId = item.getReturnLocationId();
            if (locationId == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Phiếu trả NCC thiếu vị trí xuất trả");
            }
            assertLocationInWarehouse(locationId, rma.getWarehouseId());
            stockLevelService.adjust(new StockAdjustCommand(
                    rma.getWarehouseId(),
                    locationId,
                    item.getProductId(),
                    normalizeLot(item.getLotNumber()),
                    -safeQty(item.getExpectedQty()),
                    "SUPPLIER_RETURN:" + rma.getId() + ":" + item.getId(),
                    "RMA",
                    rma.getId()
            ));
        }
    }

    private Supplier resolveSupplier(String returnType, CreateRmaRequest request) {
        if (!"SUPPLIER".equals(returnType)) {
            return null;
        }
        if (request.supplierId() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phiếu trả NCC phải có supplierId");
        }
        return supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà cung cấp"));
    }

    private boolean isSupplierReturn(Rma rma) {
        return "SUPPLIER".equals(normalizeReturnType(rma.getReturnType()));
    }

    private String normalizeReturnType(String returnType) {
        if (!StringUtils.hasText(returnType)) {
            return "CUSTOMER";
        }
        String normalized = returnType.trim().toUpperCase(Locale.ROOT);
        if ("SUPPLIER".equals(normalized) || "SUPPLIER_RETURN".equals(normalized) || "NCC".equals(normalized)) {
            return "SUPPLIER";
        }
        return "CUSTOMER";
    }

    private boolean hasAnyReceivedItem(Rma rma) {
        return rma.getItems().stream()
                .anyMatch(item -> item.getReceivedQty() != null && item.getReceivedQty() > 0);
    }

    private void notifyCreated(Rma rma) {
        String party = isSupplierReturn(rma) ? safeText(rma.getSupplierName()) : safeText(rma.getCustomerName());
        notificationService.createForRoles(
                List.of("ADMIN"),
                NotificationType.RMA_RECEIVED,
                NotificationSeverity.INFO,
                "Có RMA mới cần xử lý",
                "RMA " + rma.getRmaNumber() + " vừa được tạo cho " + party,
                "RMA",
                rma.getId());
        notifyWarehouseManagers(rma,
                "Có RMA mới cần xử lý",
                "RMA " + rma.getRmaNumber() + " vừa được tạo cho " + party,
                NotificationSeverity.INFO);
    }

    private void notifyReceived(Rma rma, int newReceivedQty, int expectedQty) {
        notificationService.createForRoles(
                List.of("ADMIN"),
                NotificationType.RMA_RECEIVED,
                NotificationSeverity.INFO,
                "Đã nhận hàng trả RMA",
                "RMA " + rma.getRmaNumber() + " vừa nhận " + newReceivedQty + "/" + expectedQty + " sản phẩm",
                "RMA",
                rma.getId());
        notifyWarehouseManagers(rma,
                "Đã nhận hàng trả RMA",
                "RMA " + rma.getRmaNumber() + " vừa nhận " + newReceivedQty + "/" + expectedQty + " sản phẩm",
                NotificationSeverity.INFO);
    }

    private void notifyWarehouseManagers(Rma rma, String title, String message, NotificationSeverity severity) {
        warehouseRepository.findById(rma.getWarehouseId())
                .map(Warehouse::getManagers)
                .orElse(Set.of())
                .stream()
                .filter(manager -> Boolean.TRUE.equals(manager.getIsActive()))
                .forEach(manager -> notificationService.create(new CreateNotificationCommand(
                        manager.getId(), NotificationType.RMA_RECEIVED, severity, title, message, "RMA", rma.getId())));
    }

    private Map<UUID, ProductSummaryResponse> loadProducts(Rma rma) {
        if (rma.getItems() == null || rma.getItems().isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = rma.getItems().stream()
                .map(RmaItem::getProductId)
                .distinct()
                .toList();
        try {
            return productService.findSummariesByIds(new ArrayList<>(ids)).stream()
                    .collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity()));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<UUID, Location> loadLocations(Rma rma) {
        if (rma.getItems() == null || rma.getItems().isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = rma.getItems().stream()
                .flatMap(item -> java.util.stream.Stream.of(
                        item.getReceivedLocationId(),
                        item.getReturnLocationId(),
                        item.getDispositionLocationId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return locationRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Location::getId, Function.identity()));
    }

    private Map<String, Object> rmaMetadata(Rma rma) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rmaNumber", rma.getRmaNumber());
        metadata.put("returnType", normalizeReturnType(rma.getReturnType()));
        metadata.put("warehouseId", rma.getWarehouseId());
        metadata.put("salesOrderId", rma.getSalesOrderId());
        metadata.put("customerId", rma.getCustomerId());
        metadata.put("supplierId", rma.getSupplierId());
        metadata.put("status", rma.getStatus() == null ? null : rma.getStatus().name());
        metadata.put("totalItems", rma.getItems() == null ? 0 : rma.getItems().size());
        return metadata;
    }

    private int safeQty(Integer qty) {
        return qty == null ? 0 : qty;
    }

    private String normalizeLot(String lotNumber) {
        return lotNumber == null ? "" : lotNumber.trim();
    }

    private String normalizeCondition(String condition) {
        return StringUtils.hasText(condition) ? condition.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String displayRma(Rma rma) {
        return StringUtils.hasText(rma.getRmaNumber()) ? rma.getRmaNumber() : rma.getId().toString();
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "khách hàng";
    }

    private List<RmaReportResponse.GroupStat> groupSupplierStats(List<Rma> rows) {
        return rows.stream()
                .filter(this::isSupplierReturn)
                .collect(Collectors.groupingBy(
                        rma -> rma.getSupplierId() == null ? "UNKNOWN" : rma.getSupplierId().toString(),
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    List<Rma> group = entry.getValue();
                    String label = group.stream()
                            .map(Rma::getSupplierName)
                            .filter(StringUtils::hasText)
                            .findFirst()
                            .orElse("Không xác định");
                    int quantity = group.stream()
                            .flatMap(rma -> rma.getItems().stream())
                            .mapToInt(item -> safeQty(item.getExpectedQty()))
                            .sum();
                    return new RmaReportResponse.GroupStat(entry.getKey(), label, group.size(), quantity);
                })
                .sorted((a, b) -> Integer.compare(b.quantity(), a.quantity()))
                .limit(10)
                .toList();
    }

    private List<RmaReportResponse.GroupStat> groupReasonStats(List<Rma> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        rma -> StringUtils.hasText(rma.getReason()) ? rma.getReason().trim() : "Không xác định",
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    int quantity = entry.getValue().stream()
                            .flatMap(rma -> rma.getItems().stream())
                            .mapToInt(item -> safeQty(item.getExpectedQty()))
                            .sum();
                    return new RmaReportResponse.GroupStat(entry.getKey(), entry.getKey(), entry.getValue().size(), quantity);
                })
                .sorted((a, b) -> Integer.compare(b.quantity(), a.quantity()))
                .limit(10)
                .toList();
    }

    private boolean containsIgnoreCase(String value, String lowercaseNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowercaseNeedle);
    }
}
