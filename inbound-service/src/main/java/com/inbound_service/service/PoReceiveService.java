package com.inbound_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.dto.request.ReceivePoItemRequest;
import com.inbound_service.dto.response.ReceivePoItemResponse;
import com.inbound_service.entity.PoItem;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PutawayTask;
import com.inbound_service.mapper.PoItemMapper;
import com.inbound_service.mapper.PutawayTaskMapper;
import com.inbound_service.repository.PoItemRepository;
import com.inbound_service.repository.PurchaseOrderRepository;
import com.inbound_service.repository.PutawayTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PoReceiveService {

    private static final String PO_CANCELLED = "CANCELLED";

    private final PoItemRepository poItemRepository;
    private final PutawayTaskRepository putawayTaskRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PoItemMapper poItemMapper;
    private final PutawayTaskMapper putawayTaskMapper;

    /**
     * Nhận hàng theo dòng PO: tăng received_qty, tạo putaway PENDING (cùng transaction DB inbound).
     * Cập nhật tồn kho thực hiện khi hoàn tất putaway (gọi warehouse).
     */
    @Transactional
    public ReceivePoItemResponse receive(UUID poItemId, ReceivePoItemRequest request) {
        PoItem line = poItemRepository.findByIdWithPurchaseOrder(poItemId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy dòng đơn nhập"));

        PurchaseOrder po = line.getPurchaseOrder();
        if (PO_CANCELLED.equalsIgnoreCase(po.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn nhập đã hủy, không nhận hàng");
        }

        int current = line.getReceivedQty() == null ? 0 : line.getReceivedQty();
        int next = current + request.qty();
        if (next > line.getOrderedQty()) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Số lượng nhận vượt quá đặt hàng (đã nhận " + current + ", đặt " + line.getOrderedQty() + ")");
        }

        line.setReceivedQty(next);
        poItemRepository.save(line);

        PutawayTask task = PutawayTask.builder()
                .poItem(line)
                .productId(line.getProductId())
                .qtyToPutaway(request.qty())
                .suggestedLocationId(request.suggestedLocationId())
                .status("PENDING")
                .build();
        putawayTaskRepository.save(task);

        refreshPoHeaderStatus(po.getId());

        return new ReceivePoItemResponse(
                poItemMapper.toResponse(line),
                putawayTaskMapper.toResponse(task));
    }

    private void refreshPoHeaderStatus(UUID purchaseOrderId) {
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn nhập"));
        List<PoItem> lines = poItemRepository.findByPurchaseOrderId(purchaseOrderId);
        boolean allReceived = lines.stream()
                .allMatch(l -> Objects.equals(l.getOrderedQty(), l.getReceivedQty()));
        if (allReceived) {
            po.setStatus("RECEIVED");
        } else if ("DRAFT".equalsIgnoreCase(po.getStatus())) {
            po.setStatus("RECEIVING");
        }
        purchaseOrderRepository.save(po);
    }
}
