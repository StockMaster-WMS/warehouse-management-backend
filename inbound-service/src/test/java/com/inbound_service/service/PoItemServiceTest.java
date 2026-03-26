package com.inbound_service.service;

import com.common.exception.AppException;
import com.inbound_service.dto.request.CreatePoItemRequest;
import com.inbound_service.dto.request.UpdatePoItemRequest;
import com.inbound_service.dto.response.PoItemResponse;
import com.inbound_service.entity.PoItem;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import com.inbound_service.mapper.PoItemMapper;
import com.inbound_service.repository.PoItemRepository;
import com.inbound_service.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class PoItemServiceTest {

    @Mock
    private PoItemRepository poItemRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PoItemMapper poItemMapper;

    @InjectMocks
    private PoItemService poItemService;

    @Test
    void createShouldInitializeReceivedQtyAsZero() {
        UUID poId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        PurchaseOrder po = buildPo(poId, PurchaseOrderStatus.DRAFT);

        CreatePoItemRequest request = new CreatePoItemRequest(
                poId,
                (short) 1,
                UUID.randomUUID(),
                "SKU-1",
                10,
                null);

        PoItem entity = PoItem.builder()
                .id(itemId)
                .lineNumber((short) 1)
                .productId(request.productId())
                .productSku(request.productSku())
                .orderedQty(request.orderedQty())
                .receivedQty(null)
                .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(poItemRepository.findByPurchaseOrderIdAndLineNumber(poId, (short) 1)).thenReturn(Optional.empty());
        when(poItemMapper.toEntity(request)).thenReturn(entity);
        when(poItemRepository.save(any(PoItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(poItemMapper.toResponse(any(PoItem.class))).thenReturn(new PoItemResponse(
                itemId, poId, (short) 1, request.productId(), "SKU-1", 10, 0, null));

        PoItemResponse response = poItemService.create(request);

        assertThat(response.receivedQty()).isZero();
        assertThat(entity.getReceivedQty()).isZero();
        verify(poItemRepository).save(entity);
    }

    @Test
    void updateShouldRejectMovingLineToAnotherPo() {
        UUID currentPoId = UUID.randomUUID();
        UUID anotherPoId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        PurchaseOrder currentPo = buildPo(currentPoId, PurchaseOrderStatus.DRAFT);
        PurchaseOrder anotherPo = buildPo(anotherPoId, PurchaseOrderStatus.DRAFT);
        PoItem item = buildItem(itemId, currentPo, 10, 2);

        UpdatePoItemRequest request = new UpdatePoItemRequest(
                anotherPoId,
                (short) 1,
                item.getProductId(),
                item.getProductSku(),
                10,
                null);

        when(poItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(purchaseOrderRepository.findById(anotherPoId)).thenReturn(Optional.of(anotherPo));

        assertThatThrownBy(() -> poItemService.update(itemId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không được chuyển dòng");

        verify(poItemRepository, never()).save(any());
    }

    @Test
    void deleteShouldRejectLineAlreadyReceived() {
        UUID poId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        PurchaseOrder po = buildPo(poId, PurchaseOrderStatus.RECEIVING);
        PoItem item = buildItem(itemId, po, 10, 1);

        when(poItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> poItemService.delete(itemId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đã có số lượng nhận");

        verify(poItemRepository, never()).delete(any(PoItem.class));
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

    private static PoItem buildItem(UUID id, PurchaseOrder po, int orderedQty, int receivedQty) {
        return PoItem.builder()
                .id(id)
                .purchaseOrder(po)
                .lineNumber((short) 1)
                .productId(UUID.randomUUID())
                .productSku("SKU-1")
                .orderedQty(orderedQty)
                .receivedQty(receivedQty)
                .build();
    }
}
