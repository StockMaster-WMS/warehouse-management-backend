package com.inbound_service.service;

import com.common.audit.AuditLogService;
import com.common.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inbound_service.dto.request.CreateInboundReceiptRequest;
import com.inbound_service.dto.request.ReceiveLineRequest;
import com.inbound_service.dto.response.InboundReceiptResponse;
import com.inbound_service.entity.InboundReceipt;
import com.inbound_service.entity.InboundReceiptStatus;
import com.inbound_service.mapper.InboundReceiptMapper;
import com.inbound_service.repository.InboundReceiptRepository;
import com.inbound_service.repository.PoItemRepository;
import com.inbound_service.repository.PurchaseOrderRepository;
import com.inbound_service.repository.PutawayTaskRepository;
import com.product_service.repository.ProductRepository;
import com.product_service.repository.SupplierRepository;
import com.warehouse_service.service.StockLevelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboundReceiptIdempotencyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private InboundReceiptRepository receiptRepository;
    private PurchaseOrderRepository purchaseOrderRepository;
    private StockLevelService stockLevelService;
    private InboundReceiptMapper receiptMapper;
    private InboundReceiptService service;

    @BeforeEach
    void setUp() {
        receiptRepository = mock(InboundReceiptRepository.class);
        purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        stockLevelService = mock(StockLevelService.class);
        receiptMapper = mock(InboundReceiptMapper.class);
        service = new InboundReceiptService(
                receiptRepository,
                purchaseOrderRepository,
                mock(PoItemRepository.class),
                mock(PutawayTaskRepository.class),
                stockLevelService,
                receiptMapper,
                mock(AuditLogService.class),
                mock(SupplierRepository.class),
                mock(ProductRepository.class),
                objectMapper);
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsExistingReceiptWithoutMutatingStock() throws Exception {
        CreateInboundReceiptRequest request = request(5);
        String key = "receive-key-1";
        InboundReceipt existing = existingReceipt(key, requestHash(request));
        InboundReceiptResponse response = response(existing);

        when(receiptRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));
        when(receiptMapper.toResponse(existing)).thenReturn(response);

        InboundReceiptResponse result = service.createReceipt(request, key);

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(purchaseOrderRepository, never()).findByIdForUpdate(request.purchaseOrderId());
        verify(stockLevelService, never()).adjust(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reusedIdempotencyKeyWithDifferentPayloadIsRejected() {
        CreateInboundReceiptRequest request = request(5);
        InboundReceipt existing = existingReceipt("receive-key-1", "different-hash");
        when(receiptRepository.findByIdempotencyKey("receive-key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createReceipt(request, "receive-key-1"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("payload khác");
    }

    private static CreateInboundReceiptRequest request(int receivedQty) {
        return new CreateInboundReceiptRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "note",
                List.of(new ReceiveLineRequest(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        receivedQty,
                        "line")));
    }

    private static InboundReceipt existingReceipt(String key, String requestHash) {
        InboundReceipt receipt = InboundReceipt.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000010"))
                .receiptNumber("GRN-1")
                .warehouseId(UUID.fromString("00000000-0000-0000-0000-000000000020"))
                .locationId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .status(InboundReceiptStatus.RECEIVED)
                .receivedDate(LocalDate.now())
                .idempotencyKey(key)
                .requestHash(requestHash)
                .build();
        receipt.setItems(List.of());
        return receipt;
    }

    private static InboundReceiptResponse response(InboundReceipt receipt) {
        return new InboundReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                null,
                null,
                receipt.getWarehouseId(),
                receipt.getLocationId(),
                receipt.getStatus().name(),
                receipt.getNote(),
                receipt.getReceivedDate(),
                receipt.getReceivedBy(),
                receipt.getCreatedAt(),
                List.of());
    }

    private String requestHash(CreateInboundReceiptRequest request) throws Exception {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("purchaseOrderId", request.purchaseOrderId());
        canonical.put("locationId", request.locationId());
        canonical.put("note", request.note() == null ? "" : request.note().trim());
        List<Map<String, Object>> lines = request.items().stream()
                .map(line -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("poItemId", line.poItemId());
                    value.put("receivedQty", line.receivedQty());
                    value.put("note", line.note() == null ? "" : line.note().trim());
                    return value;
                })
                .sorted(Comparator
                        .comparing((Map<String, Object> line) -> String.valueOf(line.get("poItemId")))
                        .thenComparing(line -> String.valueOf(line.get("receivedQty")))
                        .thenComparing(line -> String.valueOf(line.get("note"))))
                .toList();
        canonical.put("items", lines);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(objectMapper.writeValueAsBytes(canonical)));
    }
}
