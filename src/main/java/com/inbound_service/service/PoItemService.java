package com.inbound_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.dto.request.AddPurchaseOrderItemRequest;
import com.inbound_service.dto.request.CreatePoItemRequest;
import com.inbound_service.dto.request.UpdatePoItemRequest;
import com.inbound_service.dto.response.PoItemResponse;
import com.inbound_service.entity.PoItem;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import com.inbound_service.mapper.PoItemMapper;
import com.inbound_service.repository.PoItemRepository;
import com.inbound_service.repository.PoItemSpecification;
import com.inbound_service.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Collection;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PoItemService {

    private final PoItemRepository poItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PoItemMapper poItemMapper;

    // Lấy danh sách dòng đơn nhập có phân trang và tìm kiếm.
    public PagedResponse<PoItemResponse> findAll(Pageable pageable, UUID purchaseOrderId, String keyword) {
        Specification<PoItem> spec = PoItemSpecification.hasPurchaseOrderId(purchaseOrderId)
                .and(PoItemSpecification.hasKeyword(keyword));
        Page<PoItem> page = poItemRepository.findAll(spec, pageable);
        Page<PoItemResponse> mapped = page.map(poItemMapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    // Lấy chi tiết dòng đơn nhập theo id.
    public PoItemResponse findById(UUID id) {
        return poItemMapper.toResponse(getPoItem(id));
    }

    // Tạo mới dòng đơn nhập.
    @Transactional
    public PoItemResponse create(CreatePoItemRequest request) {
        return create(request, null);
    }

    @Transactional
    public PoItemResponse create(CreatePoItemRequest request, Collection<UUID> visibleWarehouseIds) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(request.purchaseOrderId());
        assertPoVisible(purchaseOrder, visibleWarehouseIds);
        requirePoEditable(purchaseOrder);
        validateOrderedQty(request.orderedQty());
        ensureLineNumberUnique(request.purchaseOrderId(), request.lineNumber(), null);

        PoItem item = poItemMapper.toEntity(request);
        item.setPurchaseOrder(purchaseOrder);
        item.setReceivedQty(0);

        return poItemMapper.toResponse(poItemRepository.save(item));
    }

    @Transactional
    public PoItemResponse addToPurchaseOrder(UUID purchaseOrderId, AddPurchaseOrderItemRequest request,
            Collection<UUID> visibleWarehouseIds) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(purchaseOrderId);
        assertPoVisible(purchaseOrder, visibleWarehouseIds);
        requirePoEditable(purchaseOrder);
        validateOrderedQty(request.orderedQty());

        short lineNumber = resolveLineNumber(purchaseOrderId, request.lineNumber());
        ensureLineNumberUnique(purchaseOrderId, lineNumber, null);

        PoItem item = PoItem.builder()
                .purchaseOrder(purchaseOrder)
                .lineNumber(lineNumber)
                .productId(request.productId())
                .productSku(request.productSku())
                .productName(request.productName())
                .orderedQty(request.orderedQty())
                .receivedQty(0)
                .unitPrice(request.unitPrice())
                .build();

        return poItemMapper.toResponse(poItemRepository.save(item));
    }

    // Cập nhật dòng đơn nhập theo id.
    @Transactional
    public PoItemResponse update(UUID id, UpdatePoItemRequest request) {
        return update(id, request, null);
    }

    @Transactional
    public PoItemResponse update(UUID id, UpdatePoItemRequest request, Collection<UUID> visibleWarehouseIds) {
        PoItem item = getPoItem(id);
        PurchaseOrder purchaseOrder = getPurchaseOrder(request.purchaseOrderId());
        assertPoVisible(item.getPurchaseOrder(), visibleWarehouseIds);
        assertPoVisible(purchaseOrder, visibleWarehouseIds);
        requirePoEditable(item.getPurchaseOrder());
        requirePoEditable(purchaseOrder);
        if (!item.getPurchaseOrder().getId().equals(request.purchaseOrderId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không được chuyển dòng đơn nhập sang PO khác");
        }
        validateOrderedQty(request.orderedQty());
        int currentReceived = item.getReceivedQty() == null ? 0 : item.getReceivedQty();
        if (request.orderedQty() < currentReceived) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Số lượng đặt không được nhỏ hơn số lượng đã nhận");
        }
        ensureLineNumberUnique(request.purchaseOrderId(), request.lineNumber(), id);

        poItemMapper.updateEntity(request, item);
        item.setPurchaseOrder(purchaseOrder);

        return poItemMapper.toResponse(poItemRepository.save(item));
    }

    // Xóa dòng đơn nhập khi chưa phát sinh nhận hàng.
    @Transactional
    public void delete(UUID id) {
        delete(id, null);
    }

    @Transactional
    public void delete(UUID id, Collection<UUID> visibleWarehouseIds) {
        PoItem item = getPoItem(id);
        assertPoVisible(item.getPurchaseOrder(), visibleWarehouseIds);
        requirePoEditable(item.getPurchaseOrder());
        if ((item.getReceivedQty() != null ? item.getReceivedQty() : 0) > 0) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không thể xóa dòng đơn nhập đã có số lượng nhận");
        }
        poItemRepository.delete(item);
    }

    // Tìm thực thể dòng đơn nhập theo id.
    private PoItem getPoItem(UUID id) {
        return poItemRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn nhập"));
    }

    // Tìm thực thể đơn nhập theo id.
    private PurchaseOrder getPurchaseOrder(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập"));
    }

    // Kiểm tra trùng số dòng trong cùng đơn nhập.
    private void ensureLineNumberUnique(UUID poId, Short lineNumber, UUID currentItemId) {
        poItemRepository.findByPurchaseOrderIdAndLineNumber(poId, lineNumber)
                .filter(existing -> currentItemId == null || !existing.getId().equals(currentItemId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Số dòng đã tồn tại trong đơn nhập");
                });
    }

    private short resolveLineNumber(UUID purchaseOrderId, Short requestedLineNumber) {
        if (requestedLineNumber != null) {
            if (requestedLineNumber <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "So dong phai lon hon 0");
            }
            return requestedLineNumber;
        }
        short currentMax = poItemRepository.findMaxLineNumberByPurchaseOrderId(purchaseOrderId);
        if (currentMax == Short.MAX_VALUE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Don nhap da dat toi da so dong cho phep");
        }
        return (short) (currentMax + 1);
    }

    // Bắt buộc PO ở trạng thái DRAFT mới cho sửa dòng.
    private void requirePoEditable(PurchaseOrder purchaseOrder) {
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ chỉnh sửa dòng đơn nhập khi PO ở trạng thái DRAFT");
        }
    }

    private void assertPoVisible(PurchaseOrder purchaseOrder, Collection<UUID> visibleWarehouseIds) {
        if (visibleWarehouseIds != null && (visibleWarehouseIds.isEmpty()
                || !visibleWarehouseIds.contains(purchaseOrder.getWarehouseId()))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được phân quyền thao tác kho này");
        }
    }

    // Kiểm tra số lượng đặt hợp lệ.
    private void validateOrderedQty(Integer orderedQty) {
        if (orderedQty <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng đặt phải lớn hơn 0");
        }
    }
}
