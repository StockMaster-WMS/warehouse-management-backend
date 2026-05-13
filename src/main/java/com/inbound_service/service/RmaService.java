package com.inbound_service.service;

import com.common.api.stock.StockAdjustCommand;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.util.CodeGenerator;
import com.inbound_service.dto.request.CreateRmaRequest;
import com.inbound_service.dto.request.ReceiveRmaRequest;
import com.inbound_service.dto.response.RmaResponse;
import com.inbound_service.entity.Rma;
import com.inbound_service.entity.RmaItem;
import com.inbound_service.repository.RmaRepository;
import com.warehouse_service.service.StockLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaService {

    private final RmaRepository rmaRepository;
    private final StockLevelService stockLevelService;

    public List<RmaResponse> getAll() {
        return rmaRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public RmaResponse getById(UUID id) {
        return rmaRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu trả hàng"));
    }

    @Transactional
    public RmaResponse create(CreateRmaRequest request) {
        Rma rma = Rma.builder()
                .rmaNumber(CodeGenerator.generate("RMA"))
                .salesOrderId(request.salesOrderId())
                .customerName(request.customerName())
                .warehouseId(request.warehouseId())
                .reason(request.reason())
                .status(Rma.RmaStatus.REQUESTED)
                .build();

        List<RmaItem> items = request.items().stream().map(req -> RmaItem.builder()
                .rma(rma)
                .productId(req.productId())
                .expectedQty(req.expectedQty())
                .lotNumber(req.lotNumber())
                .build()).collect(Collectors.toList());

        rma.setItems(items);
        return toResponse(rmaRepository.save(rma));
    }

    @Transactional
    public RmaResponse receiveItem(UUID rmaId, ReceiveRmaRequest request) {
        Rma rma = rmaRepository.findById(rmaId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy RMA"));

        RmaItem item = rma.getItems().stream()
                .filter(i -> i.getId().equals(request.itemId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy mặt hàng trong RMA"));

        item.setReceivedQty(request.receivedQty());
        item.setCondition(request.condition());
        item.setNotes(request.notes());

        // Increment stock if location is provided
        if (request.locationId() != null && request.receivedQty() > 0) {
            stockLevelService.adjust(new StockAdjustCommand(
                    rma.getWarehouseId(),
                    request.locationId(),
                    item.getProductId(),
                    item.getLotNumber(),
                    request.receivedQty(),
                    "RMA_RECEIVE:" + rma.getId() + ":" + item.getId(),
                    "RMA",
                    rma.getId()
            ));
        }

        if (rma.getStatus() == Rma.RmaStatus.REQUESTED) {
            rma.setStatus(Rma.RmaStatus.RECEIVED);
        }

        return toResponse(rmaRepository.save(rma));
    }

    @Transactional
    public RmaResponse complete(UUID id) {
        Rma rma = rmaRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy RMA"));

        rma.setStatus(Rma.RmaStatus.COMPLETED);
        rma.setCompletedAt(OffsetDateTime.now());
        return toResponse(rmaRepository.save(rma));
    }

    private RmaResponse toResponse(Rma rma) {
        List<RmaResponse.ItemResponse> items = rma.getItems().stream()
                .map(i -> new RmaResponse.ItemResponse(
                        i.getId(),
                        i.getProductId(),
                        i.getExpectedQty(),
                        i.getReceivedQty(),
                        i.getLotNumber(),
                        i.getCondition(),
                        i.getNotes()
                )).collect(Collectors.toList());

        return new RmaResponse(
                rma.getId(),
                rma.getRmaNumber(),
                rma.getSalesOrderId(),
                rma.getCustomerName(),
                rma.getStatus().name(),
                rma.getReason(),
                rma.getWarehouseId(),
                rma.getCreatedAt(),
                rma.getCompletedAt(),
                items
        );
    }
}
