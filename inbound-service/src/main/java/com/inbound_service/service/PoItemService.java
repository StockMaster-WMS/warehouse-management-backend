package com.inbound_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PoItemService {

    private final PoItemRepository poItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PoItemMapper poItemMapper;

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

    public PoItemResponse findById(UUID id) {
        return poItemMapper.toResponse(getPoItem(id));
    }

    @Transactional
    public PoItemResponse create(CreatePoItemRequest request) {
        PurchaseOrder purchaseOrder = getPurchaseOrder(request.purchaseOrderId());
        requirePoEditable(purchaseOrder);
        validateOrderedQty(request.orderedQty());
        ensureLineNumberUnique(request.purchaseOrderId(), request.lineNumber(), null);

        PoItem item = poItemMapper.toEntity(request);
        item.setPurchaseOrder(purchaseOrder);
        item.setReceivedQty(0);

        return poItemMapper.toResponse(poItemRepository.save(item));
    }

    @Transactional
    public PoItemResponse update(UUID id, UpdatePoItemRequest request) {
        PoItem item = getPoItem(id);
        PurchaseOrder purchaseOrder = getPurchaseOrder(request.purchaseOrderId());
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

    @Transactional
    public void delete(UUID id) {
        PoItem item = getPoItem(id);
        requirePoEditable(item.getPurchaseOrder());
        if ((item.getReceivedQty() != null ? item.getReceivedQty() : 0) > 0) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Không thể xóa dòng đơn nhập đã có số lượng nhận");
        }
        poItemRepository.delete(item);
    }

    private PoItem getPoItem(UUID id) {
        return poItemRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn nhập"));
    }

    private PurchaseOrder getPurchaseOrder(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập"));
    }

    private void ensureLineNumberUnique(UUID poId, Short lineNumber, UUID currentItemId) {
        poItemRepository.findByPurchaseOrderIdAndLineNumber(poId, lineNumber)
                .filter(existing -> currentItemId == null || !existing.getId().equals(currentItemId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Số dòng đã tồn tại trong đơn nhập");
                });
    }

    private void requirePoEditable(PurchaseOrder purchaseOrder) {
        if (purchaseOrder.getStatus() == PurchaseOrderStatus.RECEIVED
                || purchaseOrder.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Đơn nhập đã kết thúc, không thể chỉnh sửa dòng");
        }
    }

    private void validateOrderedQty(Integer orderedQty) {
        if (orderedQty <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng đặt phải lớn hơn 0");
        }
    }
}
