package com.inbound_service.service;

import com.common.exception.AppException;
import com.inbound_service.dto.response.PoItemResponse;
import com.inbound_service.dto.response.PurchaseOrderDetailResponse;
import com.inbound_service.dto.response.PurchaseOrderResponse;
import com.inbound_service.dto.response.PutawayTaskResponse;
import com.inbound_service.entity.PoItem;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import com.inbound_service.entity.PutawayTask;
import com.inbound_service.mapper.PoItemMapper;
import com.inbound_service.mapper.PurchaseOrderMapper;
import com.inbound_service.mapper.PutawayTaskMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PoItemRepository poItemRepository;

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;

    @Mock
    private PutawayTaskRepository putawayTaskRepository;

    @Mock
    private PoItemMapper poItemMapper;

    @Mock
    private PutawayTaskMapper putawayTaskMapper;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    @Test
    void confirmShouldRejectWhenNoItems() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = buildPo(poId, PurchaseOrderStatus.DRAFT);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(poItemRepository.findByPurchaseOrderId(poId)).thenReturn(List.of());

        assertThatThrownBy(() -> purchaseOrderService.confirm(poId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("ít nhất một dòng hàng");

        verify(purchaseOrderRepository, never()).save(po);
    }

    @Test
    void confirmShouldSetReceivingWhenValid() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = buildPo(poId, PurchaseOrderStatus.DRAFT);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(poItemRepository.findByPurchaseOrderId(poId)).thenReturn(List.of(buildPoItem(po)));
        when(purchaseOrderRepository.save(po)).thenReturn(po);
        when(purchaseOrderMapper.toResponse(po)).thenReturn(new PurchaseOrderResponse(
                poId,
                po.getPoNumber(),
                po.getSupplierId(),
                po.getWarehouseId(),
                PurchaseOrderStatus.RECEIVING.name(),
                po.getOrderDate(),
                null,
                po.getTotalAmount(),
                null
        ));

        purchaseOrderService.confirm(poId);

        verify(purchaseOrderRepository).save(po);
    }

    @Test
    void cancelShouldRejectWhenReceived() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = buildPo(poId, PurchaseOrderStatus.RECEIVED);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> purchaseOrderService.cancel(poId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đã hoàn tất");

        verify(purchaseOrderRepository, never()).save(po);
    }

        @Test
        void findDetailShouldReturnAggregatedDataForUi() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = buildPo(poId, PurchaseOrderStatus.RECEIVING);
        PoItem item = buildPoItem(po);
        PutawayTask task = PutawayTask.builder()
            .id(UUID.randomUUID())
            .poItem(item)
            .productId(item.getProductId())
            .qtyToPutaway(10)
            .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(poItemRepository.findByPurchaseOrderId(poId)).thenReturn(List.of(item));
        when(putawayTaskRepository.findByPurchaseOrderIdWithPoItem(poId)).thenReturn(List.of(task));

        when(purchaseOrderMapper.toResponse(po)).thenReturn(new PurchaseOrderResponse(
            poId,
            po.getPoNumber(),
            po.getSupplierId(),
            po.getWarehouseId(),
            po.getStatus().name(),
            po.getOrderDate(),
            null,
            po.getTotalAmount(),
            null
        ));
        when(poItemMapper.toResponse(item)).thenReturn(new PoItemResponse(
            item.getId(),
            poId,
            item.getLineNumber(),
            item.getProductId(),
            item.getProductSku(),
            item.getOrderedQty(),
            item.getReceivedQty(),
            item.getUnitPrice()
        ));
        when(putawayTaskMapper.toResponse(task)).thenReturn(new PutawayTaskResponse(
            task.getId(),
            item.getId(),
            task.getProductId(),
            task.getQtyToPutaway(),
            task.getSuggestedLocationId(),
            task.getActualLocationId(),
            task.getStatus() != null ? task.getStatus().name() : null,
            task.getAssignedTo(),
            task.getCompletedAt()
        ));

        PurchaseOrderDetailResponse detail = purchaseOrderService.findDetail(poId);

        assertThat(detail.totalOrderedQty()).isEqualTo(10);
        assertThat(detail.totalReceivedQty()).isEqualTo(0);
        assertThat(detail.fullyReceived()).isFalse();
        assertThat(detail.items()).hasSize(1);
        assertThat(detail.putawayTasks()).hasSize(1);
        }

    private static PurchaseOrder buildPo(UUID id, PurchaseOrderStatus status) {
        return PurchaseOrder.builder()
                .id(id)
                .poNumber("PO-001")
                .supplierId(UUID.randomUUID())
                .warehouseId(UUID.randomUUID())
                .status(status)
                .orderDate(LocalDate.now())
                .build();
    }

    private static com.inbound_service.entity.PoItem buildPoItem(PurchaseOrder po) {
        return com.inbound_service.entity.PoItem.builder()
                .id(UUID.randomUUID())
                .purchaseOrder(po)
                .lineNumber((short) 1)
                .productId(UUID.randomUUID())
                .productSku("SKU-1")
                .orderedQty(10)
                .receivedQty(0)
                .build();
    }
}
