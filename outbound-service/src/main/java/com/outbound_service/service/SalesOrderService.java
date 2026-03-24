package com.outbound_service.service;

import com.common.api.stock.StockAdjustCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.client.warehouse.WarehouseStockGateway;
import com.outbound_service.dto.request.CreateSalesOrderRequest;
import com.outbound_service.dto.request.UpdateSalesOrderRequest;
import com.outbound_service.dto.response.SalesOrderResponse;
import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.mapper.SalesOrderMapper;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.SalesOrderItemRepository;
import com.outbound_service.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final PickingItemRepository pickingItemRepository;
    private final WarehouseStockGateway warehouseStockGateway;
    private final SalesOrderMapper salesOrderMapper;

    public List<SalesOrderResponse> findAll() {
        return salesOrderRepository.findAll()
                .stream()
                .map(salesOrderMapper::toResponse)
                .toList();
    }

    public SalesOrderResponse findById(UUID id) {
        return salesOrderMapper.toResponse(getSalesOrder(id));
    }

    public SalesOrderResponse findBySoNumber(String soNumber) {
        return salesOrderMapper.toResponse(salesOrderRepository.findBySoNumber(soNumber)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất")));
    }

    @Transactional
    public SalesOrderResponse create(CreateSalesOrderRequest request) {
        if (salesOrderRepository.existsBySoNumber(request.soNumber())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã đơn xuất đã tồn tại");
        }

        SalesOrder salesOrder = salesOrderMapper.toEntity(request);

        return salesOrderMapper.toResponse(salesOrderRepository.save(salesOrder));
    }

    @Transactional
    public SalesOrderResponse update(UUID id, UpdateSalesOrderRequest request) {
        SalesOrder salesOrder = getSalesOrder(id);

        salesOrderRepository.findBySoNumber(request.soNumber())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã đơn xuất đã tồn tại");
                });

        salesOrderMapper.updateEntity(request, salesOrder);

        return salesOrderMapper.toResponse(salesOrderRepository.save(salesOrder));
    }

    @Transactional
    public void delete(UUID id) {
        SalesOrder salesOrder = getSalesOrder(id);
        salesOrderRepository.delete(salesOrder);
    }

    /**
     * Khi tạo picking lần đầu: PENDING → PICKING.
     */
    @Transactional
    public void notifyPickingStartedIfPending(UUID salesOrderId) {
        SalesOrder so = getSalesOrder(salesOrderId);
        if ("PENDING".equalsIgnoreCase(so.getStatus())) {
            so.setStatus("PICKING");
            salesOrderRepository.save(so);
        }
    }

    /**
     * Bắt đầu lấy hàng thủ công: yêu cầu đã có ít nhất một dòng đơn.
     */
    @Transactional
    public SalesOrderResponse startPicking(UUID id) {
        SalesOrder so = getSalesOrder(id);
        if (!"PENDING".equalsIgnoreCase(so.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ chuyển sang PICKING khi đơn đang PENDING");
        }
        if (salesOrderItemRepository.findBySalesOrder_Id(id).isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cần ít nhất một dòng đơn (so-item) trước khi picking");
        }
        so.setStatus("PICKING");
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    /**
     * PICKING → PACKED: mọi picking line của đơn phải đã PICKED và đủ số lượng.
     */
    @Transactional
    public SalesOrderResponse markPacked(UUID id) {
        SalesOrder so = getSalesOrder(id);
        if (!"PICKING".equalsIgnoreCase(so.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ đóng gói khi đơn đang PICKING");
        }
        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(id);
        if (picks.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chưa có picking item cho đơn này");
        }
        for (PickingItem p : picks) {
            int picked = p.getQtyPicked() == null ? 0 : p.getQtyPicked();
            if (!"PICKED".equalsIgnoreCase(p.getStatus()) || picked < p.getQtyToPick()) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Chưa pick đủ: kiểm tra trạng thái PICKED và qtyPicked cho từng dòng picking");
            }
        }
        so.setStatus("PACKED");
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    /**
     * PACKED → SHIPPED: trừ tồn kho theo qty đã pick, cập nhật shipped_qty từng dòng đơn.
     */
    @Transactional
    public SalesOrderResponse markShipped(UUID id) {
        SalesOrder so = getSalesOrder(id);
        if (!"PACKED".equalsIgnoreCase(so.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ giao hàng khi đơn đang PACKED");
        }
        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(id);
        if (picks.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không có picking để xuất kho");
        }

        for (PickingItem p : picks) {
            int picked = p.getQtyPicked() == null ? 0 : p.getQtyPicked();
            if (picked <= 0) {
                continue;
            }
            StockAdjustCommand cmd = new StockAdjustCommand(
                    so.getWarehouseId(),
                    p.getLocationId(),
                    p.getProductId(),
                    null,
                    -picked);
            warehouseStockGateway.adjustOrThrow(cmd);
        }

        List<SalesOrderItem> lines = salesOrderItemRepository.findBySalesOrder_Id(id);
        for (SalesOrderItem line : lines) {
            int sumPicked = pickingItemRepository.findBySoItem_Id(line.getId()).stream()
                    .mapToInt(pi -> pi.getQtyPicked() == null ? 0 : pi.getQtyPicked())
                    .sum();
            line.setShippedQty(sumPicked);
            salesOrderItemRepository.save(line);
        }

        so.setStatus("SHIPPED");
        return salesOrderMapper.toResponse(salesOrderRepository.save(so));
    }

    private SalesOrder getSalesOrder(UUID id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất"));
    }

}