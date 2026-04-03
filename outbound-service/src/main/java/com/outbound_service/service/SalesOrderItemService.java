package com.outbound_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.outbound_service.dto.request.CreateSalesOrderItemRequest;
import com.outbound_service.dto.request.UpdateSalesOrderItemRequest;
import com.outbound_service.dto.response.SalesOrderItemResponse;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.mapper.SalesOrderItemMapper;
import com.outbound_service.repository.SalesOrderItemRepository;
import com.outbound_service.repository.SalesOrderItemSpecification;
import com.outbound_service.repository.SalesOrderRepository;
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
public class SalesOrderItemService {

    private final SalesOrderItemRepository salesOrderItemRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemMapper salesOrderItemMapper;
    private final PickingItemService pickingItemService;

    // Lấy danh sách dòng đơn xuất có phân trang và tìm kiếm.
    public PagedResponse<SalesOrderItemResponse> findAll(Pageable pageable, UUID salesOrderId, String keyword) {
        Specification<SalesOrderItem> spec = SalesOrderItemSpecification.hasSalesOrderId(salesOrderId)
                .and(SalesOrderItemSpecification.hasKeyword(keyword));
        Page<SalesOrderItem> page = salesOrderItemRepository.findAll(spec, pageable);
        Page<SalesOrderItemResponse> mapped = page.map(salesOrderItemMapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    // Lấy chi tiết một dòng đơn xuất theo id.
    public SalesOrderItemResponse findById(UUID id) {
        return salesOrderItemMapper.toResponse(getLine(id));
    }

    // Tạo mới dòng đơn xuất và tự động allocate picking nếu được bật.
    @Transactional
    public SalesOrderItemResponse create(CreateSalesOrderItemRequest request) {
        SalesOrder order = getOrder(request.salesOrderId());
        requireOrderPending(order);
        ensureLineUnique(request.salesOrderId(), request.lineNumber(), null);

        SalesOrderItem item = salesOrderItemMapper.toEntity(request);
        item.setSalesOrder(order);
        SalesOrderItem saved = salesOrderItemRepository.save(item);
        boolean autoAllocate = request.autoAllocatePicking() == null || Boolean.TRUE.equals(request.autoAllocatePicking());
        if (autoAllocate) {
            pickingItemService.allocatePickingLinesForNewSoItem(saved);
        }
        return salesOrderItemMapper.toResponse(saved);
    }

    // Cập nhật thông tin dòng đơn xuất khi đơn còn ở trạng thái cho phép.
    @Transactional
    public SalesOrderItemResponse update(UUID id, UpdateSalesOrderItemRequest request) {
        SalesOrderItem item = getLine(id);
        SalesOrder order = getOrder(request.salesOrderId());
        requireOrderPending(order);
        ensureLineUnique(request.salesOrderId(), request.lineNumber(), id);

        int shipped = request.shippedQty() == null ? item.getShippedQty() : request.shippedQty();
        if (shipped < 0 || shipped > request.orderedQty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "shipped_qty không hợp lệ");
        }

        salesOrderItemMapper.updateEntity(request, item);
        item.setSalesOrder(order);
        item.setShippedQty(shipped);

        return salesOrderItemMapper.toResponse(salesOrderItemRepository.save(item));
    }

    // Xóa dòng đơn xuất theo id.
    @Transactional
    public void delete(UUID id) {
        SalesOrderItem item = getLine(id);
        requireOrderPending(item.getSalesOrder());
        salesOrderItemRepository.delete(item);
    }

    // Tìm thực thể dòng đơn xuất, ném lỗi nếu không tồn tại.
    private SalesOrderItem getLine(UUID id) {
        return salesOrderItemRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn xuất"));
    }

    // Tìm đơn xuất theo id, ném lỗi nếu không tồn tại.
    private SalesOrder getOrder(UUID id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất"));
    }

    // Bắt buộc đơn xuất đang PENDING mới được chỉnh sửa dòng đơn.
    private static void requireOrderPending(SalesOrder order) {
        if (order.getStatus() != SalesOrderStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ thêm/sửa/xóa dòng đơn khi đơn xuất đang PENDING");
        }
    }

    // Kiểm tra trùng số dòng trong cùng một đơn xuất.
    private void ensureLineUnique(UUID salesOrderId, Short lineNumber, UUID currentId) {
        salesOrderItemRepository.findBySalesOrder_IdAndLineNumber(salesOrderId, lineNumber)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(x -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Số dòng đã tồn tại trên đơn xuất");
                });
    }
}
