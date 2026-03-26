package com.inbound_service.service;

import com.common.exception.AppException;
import com.inbound_service.dto.request.ReceivePoItemRequest;
import com.inbound_service.dto.response.PoItemResponse;
import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.entity.PoItem;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import com.inbound_service.entity.PutawayTask;
import com.inbound_service.mapper.PoItemMapper;
import com.inbound_service.mapper.PutawayTaskMapper;
import com.inbound_service.repository.PoItemRepository;
import com.inbound_service.repository.PurchaseOrderRepository;
import com.inbound_service.repository.PutawayTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoReceiveServiceTest {

    @Mock
    private PoItemRepository poItemRepository;

    @Mock
    private PutawayTaskRepository putawayTaskRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PoItemMapper poItemMapper;

    @Mock
    private PutawayTaskMapper putawayTaskMapper;

    @InjectMocks
    private PoReceiveService poReceiveService;

    @Test
    void receiveShouldCreatePutawayAndMovePoToReceiving() {
        UUID poId = UUID.randomUUID();
        UUID poItemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID suggestedLocationId = UUID.randomUUID();

        PurchaseOrder po = buildPurchaseOrder(poId, PurchaseOrderStatus.RECEIVING);
        PoItem line = buildPoItem(poItemId, po, productId, 10, 2);
        when(poItemRepository.findByIdWithPurchaseOrderForUpdate(poItemId)).thenReturn(Optional.of(line));

        PutawayTask savedTask = PutawayTask.builder()
                .id(UUID.randomUUID())
                .poItem(line)
                .productId(productId)
                .qtyToPutaway(3)
                .suggestedLocationId(suggestedLocationId)
                .build();

        when(putawayTaskRepository.save(any(PutawayTask.class))).thenReturn(savedTask);
        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(poItemRepository.findByPurchaseOrderId(poId)).thenReturn(List.of(line));

        when(poItemMapper.toResponse(line)).thenReturn(new PoItemResponse(
                poItemId, poId, (short) 1, productId, "SKU-1", 10, 5, null));
        when(putawayTaskMapper.toResponse(any(PutawayTask.class))).thenReturn(new PutawayTaskResponse(
                savedTask.getId(), poItemId, productId, 3, suggestedLocationId, null, "PENDING", null, null));

        var result = poReceiveService.receive(poItemId, new ReceivePoItemRequest(3, suggestedLocationId));

        assertThat(result.poItem().receivedQty()).isEqualTo(5);
        assertThat(result.putawayTask().qtyToPutaway()).isEqualTo(3);

        ArgumentCaptor<PutawayTask> taskCaptor = ArgumentCaptor.forClass(PutawayTask.class);
        verify(putawayTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus().name()).isEqualTo("PENDING");
        assertThat(taskCaptor.getValue().getQtyToPutaway()).isEqualTo(3);

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVING);
        verify(purchaseOrderRepository).save(po);
    }

    @Test
    void receiveShouldSetPoReceivedWhenAllLinesReceived() {
        UUID poId = UUID.randomUUID();
        UUID poItemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        PurchaseOrder po = buildPurchaseOrder(poId, PurchaseOrderStatus.RECEIVING);
        PoItem line = buildPoItem(poItemId, po, productId, 10, 7);
        when(poItemRepository.findByIdWithPurchaseOrderForUpdate(poItemId)).thenReturn(Optional.of(line));
        when(putawayTaskRepository.save(any(PutawayTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        PoItem fullyReceivedLine = buildPoItem(poItemId, po, productId, 10, 10);
        when(poItemRepository.findByPurchaseOrderId(poId)).thenReturn(List.of(fullyReceivedLine));

        when(poItemMapper.toResponse(any(PoItem.class))).thenReturn(new PoItemResponse(
                poItemId, poId, (short) 1, productId, "SKU-1", 10, 10, null));
        when(putawayTaskMapper.toResponse(any(PutawayTask.class))).thenReturn(new PutawayTaskResponse(
                UUID.randomUUID(), poItemId, productId, 3, null, null, "PENDING", null, null));

        poReceiveService.receive(poItemId, new ReceivePoItemRequest(3, null));

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        verify(purchaseOrderRepository).save(po);
    }

    @Test
    void receiveShouldRejectWhenQtyExceedsOrdered() {
        UUID poItemId = UUID.randomUUID();
                PurchaseOrder po = buildPurchaseOrder(UUID.randomUUID(), PurchaseOrderStatus.RECEIVING);
        PoItem line = buildPoItem(poItemId, po, UUID.randomUUID(), 10, 9);
        when(poItemRepository.findByIdWithPurchaseOrderForUpdate(poItemId)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> poReceiveService.receive(poItemId, new ReceivePoItemRequest(2, null)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("vượt quá đặt hàng");

        verify(putawayTaskRepository, never()).save(any());
        verify(purchaseOrderRepository, never()).save(any());
    }

        @Test
        void receiveShouldRejectWhenPoIsDraft() {
                UUID poItemId = UUID.randomUUID();
                PurchaseOrder po = buildPurchaseOrder(UUID.randomUUID(), PurchaseOrderStatus.DRAFT);
                PoItem line = buildPoItem(poItemId, po, UUID.randomUUID(), 10, 0);
                when(poItemRepository.findByIdWithPurchaseOrderForUpdate(poItemId)).thenReturn(Optional.of(line));

                assertThatThrownBy(() -> poReceiveService.receive(poItemId, new ReceivePoItemRequest(1, null)))
                                .isInstanceOf(AppException.class)
                                .hasMessageContaining("chưa được xác nhận");

                verify(putawayTaskRepository, never()).save(any());
                verify(purchaseOrderRepository, never()).save(any());
        }

    private static PurchaseOrder buildPurchaseOrder(UUID id, PurchaseOrderStatus status) {
        return PurchaseOrder.builder()
                .id(id)
                .poNumber("PO-001")
                .supplierId(UUID.randomUUID())
                .warehouseId(UUID.randomUUID())
                .status(status)
                .orderDate(LocalDate.now())
                .build();
    }

    private static PoItem buildPoItem(UUID id, PurchaseOrder po, UUID productId, int ordered, int received) {
        return PoItem.builder()
                .id(id)
                .purchaseOrder(po)
                .lineNumber((short) 1)
                .productId(productId)
                .productSku("SKU-1")
                .orderedQty(ordered)
                .receivedQty(received)
                .build();
    }
}
