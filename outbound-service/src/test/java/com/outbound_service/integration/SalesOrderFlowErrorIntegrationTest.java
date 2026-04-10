package com.outbound_service.integration;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.outbound_service.client.WarehouseStockGateway;
import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.PickingItemStatus;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.SalesOrderItemRepository;
import com.outbound_service.repository.SalesOrderRepository;
import com.outbound_service.service.SalesOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "spring.datasource.url=${OUTBOUND_DB_URL:jdbc:postgresql://localhost:5432/db_outbound}",
                "spring.datasource.username=${OUTBOUND_DB_USERNAME:postgres}",
                "spring.datasource.password=${OUTBOUND_DB_PASSWORD:postgres}"
})
class SalesOrderFlowErrorIntegrationTest {

        @Autowired
        private SalesOrderService salesOrderService;

        @Autowired
        private SalesOrderRepository salesOrderRepository;

        @Autowired
        private SalesOrderItemRepository salesOrderItemRepository;

        @Autowired
        private PickingItemRepository pickingItemRepository;

        @MockBean
        private WarehouseStockGateway warehouseStockGateway;

        @BeforeEach
        void cleanDatabase() {
                pickingItemRepository.deleteAll();
                salesOrderItemRepository.deleteAll();
                salesOrderRepository.deleteAll();
        }

        @Test
        @DisplayName("startPicking thất bại khi đơn chưa có dòng hàng")
        void startPicking_shouldFail_whenOrderHasNoLines() {
                SalesOrder order = saveOrder(SalesOrderStatus.PENDING);

                AppException ex = assertThrows(AppException.class,
                                () -> salesOrderService.startPicking(order.getId()));

                assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("Cần ít nhất một dòng đơn"));
        }

        @Test
        @DisplayName("markPacked thất bại khi còn dòng picking chưa PICKED")
        void markPacked_shouldFail_whenAnyPickNotPicked() {
                SalesOrder order = saveOrder(SalesOrderStatus.PICKING);
                SalesOrderItem line = saveLine(order, 1, 10);
                savePick(line, 10, 0, PickingItemStatus.PENDING);

                AppException ex = assertThrows(AppException.class,
                                () -> salesOrderService.markPacked(order.getId()));

                assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("Chưa pick đủ"));
        }

        @Test
        @DisplayName("markPacked thất bại khi tổng picked < orderedQty theo dòng đơn")
        void markPacked_shouldFail_whenPickedSumLessThanOrderedQty() {
                SalesOrder order = saveOrder(SalesOrderStatus.PICKING);
                SalesOrderItem line = saveLine(order, 1, 10);
                savePick(line, 5, 5, PickingItemStatus.PICKED);

                AppException ex = assertThrows(AppException.class,
                                () -> salesOrderService.markPacked(order.getId()));

                assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("line 1"));
        }

        @Test
        @DisplayName("markShipped thất bại khi warehouse từ chối điều chỉnh reserved")
        void markShipped_shouldFail_whenWarehouseRejectsReservedAdjustment() {
                SalesOrder order = saveOrder(SalesOrderStatus.PACKED);
                SalesOrderItem line = saveLine(order, 1, 5);
                savePick(line, 5, 5, PickingItemStatus.PICKED);

                doThrow(new AppException(ErrorCode.BAD_REQUEST, "Warehouse từ chối điều chỉnh giữ chỗ"))
                                .when(warehouseStockGateway)
                                .adjustReservedOrThrow(any());

                AppException ex = assertThrows(AppException.class,
                                () -> salesOrderService.markShipped(order.getId()));

                assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
                SalesOrder reloaded = salesOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(SalesOrderStatus.PACKED, reloaded.getStatus());
                verify(warehouseStockGateway, never()).adjustOrThrow(any());
        }

        private SalesOrder saveOrder(SalesOrderStatus status) {
                SalesOrder order = SalesOrder.builder()
                                .soNumber("SO-IT-" + System.nanoTime())
                                .customerName("Integration Test")
                                .shippingAddress(Map.of("address", "HN"))
                                .warehouseId(UUID.randomUUID())
                                .status(status)
                                .priority((short) 5)
                                .build();
                return salesOrderRepository.save(order);
        }

        private SalesOrderItem saveLine(SalesOrder order, int lineNumber, int orderedQty) {
                SalesOrderItem line = SalesOrderItem.builder()
                                .salesOrder(order)
                                .lineNumber((short) lineNumber)
                                .productId(UUID.randomUUID())
                                .productSku("SKU-" + lineNumber)
                                .orderedQty(orderedQty)
                                .shippedQty(0)
                                .build();
                return salesOrderItemRepository.save(line);
        }

        private PickingItem savePick(SalesOrderItem line, int qtyToPick, int qtyPicked, PickingItemStatus status) {
                PickingItem pick = PickingItem.builder()
                                .soItem(line)
                                .productId(line.getProductId())
                                .locationId(UUID.randomUUID())
                                .lotNumber("")
                                .qtyToPick(qtyToPick)
                                .qtyPicked(qtyPicked)
                                .status(status)
                                .pickSequence(1)
                                .build();
                return pickingItemRepository.save(pick);
        }
}
