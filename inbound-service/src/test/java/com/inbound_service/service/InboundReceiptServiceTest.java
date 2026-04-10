package com.inbound_service.service;

import com.common.exception.AppException;
import com.inbound_service.client.WarehouseStockGateway;
import com.inbound_service.dto.request.CreateInboundReceiptRequest;
import com.inbound_service.dto.request.ReceiveLineRequest;
import com.inbound_service.entity.PoItem;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import com.inbound_service.mapper.InboundReceiptMapper;
import com.inbound_service.repository.InboundReceiptRepository;
import com.inbound_service.repository.PoItemRepository;
import com.inbound_service.repository.PurchaseOrderRepository;
import com.inbound_service.repository.PutawayTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundReceiptServiceTest {

    @Mock
    private InboundReceiptRepository receiptRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PoItemRepository poItemRepository;

    @Mock
    private PutawayTaskRepository putawayTaskRepository;

    @Mock
    private WarehouseStockGateway warehouseStockGateway;

    @Mock
    private InboundReceiptMapper receiptMapper;

    @InjectMocks
    private InboundReceiptService inboundReceiptService;

    @Test
    void createReceiptShouldRejectWhenDuplicateLinesExceedRemainingQty() {
        UUID purchaseOrderId = UUID.randomUUID();
        UUID poItemId = UUID.randomUUID();

        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .id(purchaseOrderId)
                .poNumber("PO-001")
                .supplierId(UUID.randomUUID())
                .warehouseId(UUID.randomUUID())
                .status(PurchaseOrderStatus.APPROVED)
                .orderDate(LocalDate.now())
                .build();

        PoItem poItem = PoItem.builder()
                .id(poItemId)
                .purchaseOrder(purchaseOrder)
                .lineNumber((short) 1)
                .productId(UUID.randomUUID())
                .productSku("SKU-001")
                .orderedQty(10)
                .receivedQty(2)
                .build();

        CreateInboundReceiptRequest request = new CreateInboundReceiptRequest(
                purchaseOrderId,
                UUID.randomUUID(),
                "receive duplicated lines",
                List.of(
                        new ReceiveLineRequest(poItemId, 5, null),
                        new ReceiveLineRequest(poItemId, 4, null)
                )
        );

        when(purchaseOrderRepository.findById(purchaseOrderId)).thenReturn(Optional.of(purchaseOrder));
        when(poItemRepository.findByPurchaseOrderId(purchaseOrderId)).thenReturn(List.of(poItem));

        AppException exception = assertThrows(AppException.class,
                () -> inboundReceiptService.createReceipt(request));
        assertTrue(exception.getMessage().contains("tổng số lượng nhận"));

        verify(receiptRepository, never()).save(any());
        verify(poItemRepository, never()).saveAll(any());
        verify(warehouseStockGateway, never()).adjustOrThrow(any());
        verify(putawayTaskRepository, never()).save(any());
    }
}
