package com.inbound_service.service;

import com.common.api.stock.StockAdjustCommand;
import com.common.client.warehouse.WarehouseStockGateway;
import com.common.exception.AppException;
import com.inbound_service.dto.request.CompletePutawayRequest;
import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.entity.PoItem;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import com.inbound_service.entity.PutawayStatus;
import com.inbound_service.entity.PutawayTask;
import com.inbound_service.mapper.PutawayTaskMapper;
import com.inbound_service.repository.PutawayTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PutawayTaskServiceTest {

    @Mock
    private PutawayTaskRepository putawayTaskRepository;

    @Mock
    private PutawayTaskMapper putawayTaskMapper;

    @Mock
    private WarehouseStockGateway warehouseStockGateway;

    @InjectMocks
    private PutawayTaskService putawayTaskService;

    @Test
    void completeShouldAdjustStockAndMarkCompleted() {
        UUID poId = UUID.randomUUID();
        UUID poItemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        PurchaseOrder po = PurchaseOrder.builder()
                .id(poId)
                .poNumber("PO-001")
                .supplierId(UUID.randomUUID())
                .warehouseId(warehouseId)
                .status(PurchaseOrderStatus.RECEIVING)
                .orderDate(LocalDate.now())
                .build();
        PoItem line = PoItem.builder()
                .id(poItemId)
                .purchaseOrder(po)
                .lineNumber((short) 1)
                .productId(productId)
                .productSku("SKU-1")
                .orderedQty(10)
                .receivedQty(4)
                .build();
        PutawayTask task = PutawayTask.builder()
                .id(taskId)
                .poItem(line)
                .productId(productId)
                .qtyToPutaway(4)
                .status(PutawayStatus.PENDING)
                .build();

        when(putawayTaskRepository.findByIdWithPoAndOrderForUpdate(taskId)).thenReturn(Optional.of(task));
        when(putawayTaskRepository.save(any(PutawayTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(putawayTaskMapper.toResponse(any(PutawayTask.class))).thenReturn(new PutawayTaskResponse(
                taskId, poItemId, productId, 4, null, locationId, PutawayStatus.COMPLETED.name(), null, null));

        var result = putawayTaskService.complete(taskId, new CompletePutawayRequest(locationId));

        assertThat(result.status()).isEqualTo(PutawayStatus.COMPLETED.name());
        assertThat(task.getStatus()).isEqualTo(PutawayStatus.COMPLETED);
        assertThat(task.getActualLocationId()).isEqualTo(locationId);
        assertThat(task.getCompletedAt()).isNotNull();

        ArgumentCaptor<StockAdjustCommand> cmdCaptor = ArgumentCaptor.forClass(StockAdjustCommand.class);
        verify(warehouseStockGateway).adjustOrThrow(cmdCaptor.capture());
        StockAdjustCommand sent = cmdCaptor.getValue();
        assertThat(sent.warehouseId()).isEqualTo(warehouseId);
        assertThat(sent.locationId()).isEqualTo(locationId);
        assertThat(sent.productId()).isEqualTo(productId);
        assertThat(sent.qtyDelta()).isEqualTo(4);
    }

    @Test
    void completeShouldRejectTerminalTask() {
        UUID taskId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(UUID.randomUUID())
                .poNumber("PO-001")
                .supplierId(UUID.randomUUID())
                .warehouseId(UUID.randomUUID())
                .status(PurchaseOrderStatus.RECEIVING)
                .orderDate(LocalDate.now())
                .build();
        PoItem line = PoItem.builder()
                .id(UUID.randomUUID())
                .purchaseOrder(po)
                .lineNumber((short) 1)
                .productId(UUID.randomUUID())
                .productSku("SKU-1")
                .orderedQty(5)
                .receivedQty(5)
                .build();
        PutawayTask completedTask = PutawayTask.builder()
                .id(taskId)
                .poItem(line)
                .productId(line.getProductId())
                .qtyToPutaway(5)
                .status(PutawayStatus.COMPLETED)
                .build();

        when(putawayTaskRepository.findByIdWithPoAndOrderForUpdate(taskId)).thenReturn(Optional.of(completedTask));

        assertThatThrownBy(() -> putawayTaskService.complete(taskId, new CompletePutawayRequest(UUID.randomUUID())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Chỉ hoàn tất putaway");

        verify(warehouseStockGateway, never()).adjustOrThrow(any());
        verify(putawayTaskRepository, never()).save(any());
    }
}
