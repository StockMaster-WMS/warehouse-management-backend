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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaService {

    private final RmaRepository rmaRepository;
    private final StockLevelService stockLevelService;

    public List<RmaResponse> getAll(String keyword, String status, String reason, UUID warehouseId,
            OffsetDateTime createdFrom, OffsetDateTime createdTo) {
        String normalizedKeyword = StringUtils.hasText(keyword)
                ? keyword.trim().toLowerCase(Locale.ROOT)
                : null;
        String normalizedStatus = StringUtils.hasText(status)
                ? status.trim().toUpperCase(Locale.ROOT)
                : null;
        String normalizedReason = StringUtils.hasText(reason)
                ? reason.trim().toUpperCase(Locale.ROOT)
                : null;

        return rmaRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(rma -> normalizedKeyword == null
                        || containsIgnoreCase(rma.getRmaNumber(), normalizedKeyword)
                        || containsIgnoreCase(rma.getCustomerName(), normalizedKeyword)
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
                .collect(Collectors.toList());
    }

    public RmaResponse getById(UUID id) {
        return rmaRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu trả hàng"));
    }

    @Transactional
    public RmaResponse create(CreateRmaRequest request) {
        validateCreateRequest(request);
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
        Rma rma = rmaRepository.findByIdWithItemsForUpdate(rmaId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy RMA"));

        RmaItem item = rma.getItems().stream()
                .filter(i -> i.getId().equals(request.itemId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy mặt hàng trong RMA"));

        if (rma.getStatus() == Rma.RmaStatus.COMPLETED || rma.getStatus() == Rma.RmaStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể nhận hàng cho RMA đã kết thúc");
        }

        int expectedQty = item.getExpectedQty() == null ? 0 : item.getExpectedQty();
        int oldReceivedQty = item.getReceivedQty() == null ? 0 : item.getReceivedQty();
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

        replaceRestockedQuantity(rma, item, oldReceivedQty, oldLocationId, newReceivedQty, newLocationId);

        item.setReceivedQty(newReceivedQty);
        item.setReceivedLocationId(newReceivedQty > 0 ? newLocationId : null);
        item.setCondition(request.condition());
        item.setNotes(request.notes());

        if (hasAnyReceivedItem(rma)) {
            rma.setStatus(Rma.RmaStatus.RECEIVED);
        } else {
            rma.setStatus(Rma.RmaStatus.REQUESTED);
        }

        return toResponse(rmaRepository.save(rma));
    }

    @Transactional
    public RmaResponse complete(UUID id) {
        Rma rma = rmaRepository.findByIdWithItemsForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy RMA"));

        if (rma.getStatus() == Rma.RmaStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "RMA đã hoàn tất trước đó");
        }
        if (rma.getStatus() == Rma.RmaStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể hoàn tất RMA đã hủy");
        }
        for (RmaItem item : rma.getItems()) {
            int expected = item.getExpectedQty() == null ? 0 : item.getExpectedQty();
            int received = item.getReceivedQty() == null ? 0 : item.getReceivedQty();
            if (received != expected) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Chưa nhận đủ hàng trả cho sản phẩm " + item.getProductId()
                                + " (" + received + "/" + expected + ")");
            }
        }

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
                        i.getReceivedLocationId(),
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

    private void validateCreateRequest(CreateRmaRequest request) {
        for (CreateRmaRequest.ItemRequest item : request.items()) {
            if (item.expectedQty() == null || item.expectedQty() <= 0) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng dự kiến trả phải lớn hơn 0");
            }
        }
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
                    item.getLotNumber(),
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
                    item.getLotNumber(),
                    newReceivedQty,
                    "RMA_RECEIVE:" + rma.getId() + ":" + item.getId() + ":APPLY:"
                            + newLocationId + ":" + newReceivedQty + ":FROM:" + oldReceivedQty + ":" + oldLocationId,
                    "RMA",
                    rma.getId()
            ));
        }
    }

    private boolean hasAnyReceivedItem(Rma rma) {
        return rma.getItems().stream()
                .anyMatch(item -> item.getReceivedQty() != null && item.getReceivedQty() > 0);
    }

    private boolean containsIgnoreCase(String value, String lowercaseNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowercaseNeedle);
    }
}
