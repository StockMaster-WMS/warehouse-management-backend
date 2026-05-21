package com.outbound_service.service;

import com.common.api.stock.StockAdjustCommand;
import com.common.api.stock.StockReserveCommand;
import com.common.audit.AuditLogService;
import com.common.notification.NotificationService;
import com.outbound_service.dto.request.UpdatePickingItemRequest;
import com.outbound_service.dto.response.PickingItemResponse;
import com.outbound_service.dto.response.SalesOrderResponse;
import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.PickingItemStatus;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.mapper.PickingItemMapper;
import com.outbound_service.mapper.SalesOrderMapper;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.CustomerRepository;
import com.outbound_service.repository.SalesOrderItemRepository;
import com.outbound_service.repository.SalesOrderRepository;
import com.product_service.repository.ProductRepository;
import com.product_service.service.ProductService;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.service.LocationService;
import com.warehouse_service.service.StockLevelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundStockFlowTest {

    @Mock
    private PickingItemRepository pickingItemRepository;
    @Mock
    private SalesOrderItemRepository salesOrderItemRepository;
    @Mock
    private PickingItemMapper pickingItemMapper;
    @Mock
    private StockLevelService stockLevelService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private ProductService productService;
    @Mock
    private LocationService locationService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private SalesOrderMapper salesOrderMapper;
    @Mock
    private CustomerRepository customerRepository;

    private PickingItemService pickingItemService;
    private SalesOrderService salesOrderService;

    @BeforeEach
    void setUp() {
        pickingItemService = new PickingItemService(
                pickingItemRepository,
                salesOrderItemRepository,
                pickingItemMapper,
                stockLevelService,
                productRepository,
                locationRepository,
                productService,
                locationService,
                auditLogService,
                notificationService);
        salesOrderService = new SalesOrderService(
                salesOrderRepository,
                salesOrderItemRepository,
                pickingItemRepository,
                stockLevelService,
                salesOrderMapper,
                auditLogService,
                notificationService,
                customerRepository);
    }

    @Test
    void completeMobileMarksPickedWithoutMutatingStock() {
        UUID orderId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID pickId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        SalesOrder order = salesOrder(orderId, warehouseId, SalesOrderStatus.PICKING);
        SalesOrderItem line = salesOrderItem(lineId, order, productId, 5);
        PickingItem pick = pickingItem(pickId, line, productId, locationId, 5, 0, PickingItemStatus.PENDING);

        when(pickingItemRepository.findByIdWithSoAndOrder(pickId)).thenReturn(Optional.of(pick));
        when(salesOrderItemRepository.findByIdWithSalesOrderForUpdate(lineId)).thenReturn(Optional.of(line));
        when(pickingItemRepository.findBySoItem_Id(lineId)).thenReturn(List.of(pick));
        when(pickingItemRepository.save(any(PickingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pickingItemMapper.toResponse(any(PickingItem.class))).thenAnswer(invocation -> pickingResponse(invocation.getArgument(0)));
        doAnswer(invocation -> {
            UpdatePickingItemRequest request = invocation.getArgument(0);
            PickingItem target = invocation.getArgument(1);
            target.setProductId(request.productId());
            target.setLocationId(request.locationId());
            target.setQtyToPick(request.qtyToPick());
            target.setQtyPicked(request.qtyPicked());
            target.setPickSequence(request.pickSequence());
            target.setLotNumber(request.lotNumber() == null ? "" : request.lotNumber());
            return null;
        }).when(pickingItemMapper).updateEntity(any(UpdatePickingItemRequest.class), any(PickingItem.class));

        PickingItemResponse response = pickingItemService.completeMobile(pickId);

        assertThat(response.status()).isEqualTo("PICKED");
        assertThat(response.qtyPicked()).isEqualTo(5);
        verify(stockLevelService, never()).adjust(any(StockAdjustCommand.class));
        verify(stockLevelService, never()).adjustReserved(any(StockReserveCommand.class));
    }

    @Test
    void markShippedReleasesReservedAndDeductsOnHandOnce() {
        UUID orderId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID pickId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        SalesOrder order = salesOrder(orderId, warehouseId, SalesOrderStatus.PACKED);
        SalesOrderItem line = salesOrderItem(lineId, order, productId, 5);
        PickingItem pick = pickingItem(pickId, line, productId, locationId, 5, 5, PickingItemStatus.PICKED);

        when(salesOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(pickingItemRepository.findBySalesOrderIdWithSoItem(orderId)).thenReturn(List.of(pick));
        when(salesOrderItemRepository.findBySalesOrder_Id(orderId)).thenReturn(List.of(line));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderMapper.toResponse(any(SalesOrder.class))).thenAnswer(invocation -> salesOrderResponse(invocation.getArgument(0)));

        SalesOrderResponse response = salesOrderService.markShipped(orderId);

        ArgumentCaptor<StockReserveCommand> reserveCaptor = ArgumentCaptor.forClass(StockReserveCommand.class);
        ArgumentCaptor<StockAdjustCommand> adjustCaptor = ArgumentCaptor.forClass(StockAdjustCommand.class);
        verify(stockLevelService).adjustReserved(reserveCaptor.capture());
        verify(stockLevelService).adjust(adjustCaptor.capture());

        assertThat(response.status()).isEqualTo("SHIPPED");
        assertThat(line.getShippedQty()).isEqualTo(5);
        assertThat(reserveCaptor.getValue().reservedDelta()).isEqualTo(-5);
        assertThat(adjustCaptor.getValue().qtyDelta()).isEqualTo(-5);
        assertThat(adjustCaptor.getValue().idempotencyKey()).contains("SHIP_ADJUST");
    }

    private static SalesOrder salesOrder(UUID id, UUID warehouseId, SalesOrderStatus status) {
        return SalesOrder.builder()
                .id(id)
                .soNumber("SO-1")
                .customerName("Customer")
                .shippingAddress(Map.of())
                .warehouseId(warehouseId)
                .status(status)
                .build();
    }

    private static SalesOrderItem salesOrderItem(UUID id, SalesOrder order, UUID productId, int orderedQty) {
        return SalesOrderItem.builder()
                .id(id)
                .salesOrder(order)
                .lineNumber((short) 1)
                .productId(productId)
                .productSku("SKU-1")
                .orderedQty(orderedQty)
                .shippedQty(0)
                .build();
    }

    private static PickingItem pickingItem(UUID id, SalesOrderItem line, UUID productId, UUID locationId,
            int qtyToPick, int qtyPicked, PickingItemStatus status) {
        return PickingItem.builder()
                .id(id)
                .soItem(line)
                .productId(productId)
                .locationId(locationId)
                .lotNumber("")
                .qtyToPick(qtyToPick)
                .qtyPicked(qtyPicked)
                .status(status)
                .build();
    }

    private static PickingItemResponse pickingResponse(PickingItem item) {
        return new PickingItemResponse(
                item.getId(),
                item.getSoItem().getId(),
                item.getProductId(),
                item.getLocationId(),
                item.getLotNumber(),
                item.getQtyToPick(),
                item.getQtyPicked(),
                item.getStatus().name(),
                item.getPickSequence(),
                item.getSoItem().getSalesOrder().getSoNumber(),
                item.getSoItem().getProductSku(),
                null,
                null,
                null,
                null,
                item.getAssigneeId());
    }

    private static SalesOrderResponse salesOrderResponse(SalesOrder order) {
        return new SalesOrderResponse(
                order.getId(),
                order.getSoNumber(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getShippingAddress(),
                order.getWarehouseId(),
                order.getPriority(),
                order.getStatus().name(),
                order.getCreatedAt(),
                List.of());
    }
}
