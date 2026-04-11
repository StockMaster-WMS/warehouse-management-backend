package com.outbound_service.service;

import com.common.exception.AppException;
import com.outbound_service.client.LocationClient;
import com.outbound_service.client.ProductClient;
import com.outbound_service.client.WarehouseStockGateway;
import com.outbound_service.dto.request.CreatePickingItemRequest;
import com.outbound_service.dto.request.UpdatePickingItemRequest;
import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.PickingItemStatus;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.mapper.PickingItemMapper;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.SalesOrderItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PickingItemServiceTest {

    @Mock
    private PickingItemRepository pickingItemRepository;

    @Mock
    private SalesOrderItemRepository salesOrderItemRepository;

    @Mock
    private PickingItemMapper pickingItemMapper;

    @Mock
    private WarehouseStockGateway warehouseStockGateway;

    @Mock
    private ProductClient productClient;

    @Mock
    private LocationClient locationClient;

    @InjectMocks
    private PickingItemService pickingItemService;

    @Test
    void createShouldRejectWhenTotalAllocatedExceedsOrderedQty() {
        UUID soItemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        SalesOrder order = SalesOrder.builder()
                .id(UUID.randomUUID())
                .soNumber("SO-001")
                .customerName("Customer")
                .shippingAddress(Map.of("address", "HN"))
                .warehouseId(UUID.randomUUID())
                .status(SalesOrderStatus.PENDING)
                .build();

        SalesOrderItem line = SalesOrderItem.builder()
                .id(soItemId)
                .salesOrder(order)
                .lineNumber((short) 1)
                .productId(productId)
                .productSku("SKU-001")
                .orderedQty(10)
                .shippedQty(0)
                .build();

        PickingItem existingPick = PickingItem.builder()
                .id(UUID.randomUUID())
                .soItem(line)
                .productId(productId)
                .locationId(UUID.randomUUID())
                .lotNumber("")
                .qtyToPick(7)
                .qtyPicked(0)
                .status(PickingItemStatus.PENDING)
                .build();

        CreatePickingItemRequest request = new CreatePickingItemRequest(
                soItemId,
                productId,
                UUID.randomUUID(),
                4,
                0,
                "PENDING",
                1,
                ""
        );

        when(salesOrderItemRepository.findByIdWithSalesOrder(soItemId)).thenReturn(Optional.of(line));
        when(pickingItemRepository.findBySoItem_Id(soItemId)).thenReturn(List.of(existingPick));

        assertThatThrownBy(() -> pickingItemService.create(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Tổng qtyToPick cho line 1");

        verify(pickingItemRepository, never()).save(any());
        verifyNoInteractions(warehouseStockGateway);
    }

    @Test
    void updateShouldRejectMovingPickToAnotherSalesOrderLine() {
        UUID salesOrderId = UUID.randomUUID();
        UUID soItemId = UUID.randomUUID();
        UUID pickId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        SalesOrder order = SalesOrder.builder()
                .id(salesOrderId)
                .soNumber("SO-002")
                .customerName("Customer")
                .shippingAddress(Map.of("address", "HCM"))
                .warehouseId(UUID.randomUUID())
                .status(SalesOrderStatus.PICKING)
                .build();

        SalesOrderItem line = SalesOrderItem.builder()
                .id(soItemId)
                .salesOrder(order)
                .lineNumber((short) 1)
                .productId(productId)
                .productSku("SKU-002")
                .orderedQty(5)
                .shippedQty(0)
                .build();

        PickingItem existingPick = PickingItem.builder()
                .id(pickId)
                .soItem(line)
                .productId(productId)
                .locationId(UUID.randomUUID())
                .lotNumber("")
                .qtyToPick(5)
                .qtyPicked(0)
                .status(PickingItemStatus.PENDING)
                .build();

        UpdatePickingItemRequest request = new UpdatePickingItemRequest(
                UUID.randomUUID(),
                productId,
                existingPick.getLocationId(),
                5,
                0,
                "PENDING",
                1,
                ""
        );

        when(pickingItemRepository.findByIdWithSoAndOrder(pickId)).thenReturn(Optional.of(existingPick));

        assertThatThrownBy(() -> pickingItemService.update(pickId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không được chuyển picking sang dòng đơn xuất khác");

        verifyNoInteractions(salesOrderItemRepository, pickingItemMapper, warehouseStockGateway);
    }
}
