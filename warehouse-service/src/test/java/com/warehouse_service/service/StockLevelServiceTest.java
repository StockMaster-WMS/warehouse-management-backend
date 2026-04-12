package com.warehouse_service.service;

import com.common.api.ApiResponse;
import com.common.api.stock.StockAdjustCommand;
import com.warehouse_service.client.ProductBatchClient;
import com.warehouse_service.dto.response.ProductSummary;
import com.warehouse_service.dto.response.StockLevelExpandedResponse;
import com.warehouse_service.dto.response.StockLevelResponse;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.StockMovement;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.StockLevelMapper;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.repository.StockMovementRepository;
import com.warehouse_service.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLevelServiceTest {

    @Mock
    private StockLevelRepository stockLevelRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private StockLevelMapper stockLevelMapper;

    @Mock
    private ProductBatchClient productBatchClient;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private StockLevelService stockLevelService;

    @Test
    void findLowStockShouldUseProjectionAndFetchOnlyLowStockRows() {
        UUID lowStockId = UUID.randomUUID();
        UUID normalStockId = UUID.randomUUID();
        UUID lowProductId = UUID.randomUUID();
        UUID normalProductId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        StockLevelRepository.StockQuantityView lowStock =
                quantityView(lowStockId, lowProductId, 4, 1);
        StockLevelRepository.StockQuantityView normalStock =
                quantityView(normalStockId, normalProductId, 12, 0);

        Warehouse warehouse = Warehouse.builder()
                .id(warehouseId)
                .code("WH-01")
                .name("Main warehouse")
                .build();
        Location location = Location.builder()
                .id(locationId)
                .warehouse(warehouse)
                .code("FAST-A01")
                .zone("FAST")
                .build();
        StockLevel stockEntity = StockLevel.builder()
                .id(lowStockId)
                .warehouse(warehouse)
                .location(location)
                .productId(lowProductId)
                .lotNumber("")
                .qtyOnHand(4)
                .qtyReserved(1)
                .build();

        when(stockLevelRepository.findQuantityViews()).thenReturn(List.of(lowStock, normalStock));
        when(productBatchClient.getByIds(ArgumentMatchers.<List<UUID>>any()))
                .thenReturn(ApiResponse.success("ok", List.of(
                        new ProductSummary(lowProductId, "LOW-001", "Low stock product", 5),
                        new ProductSummary(normalProductId, "OK-001", "Normal stock product", 5))));
        when(stockLevelRepository.findByIdInWithWarehouseAndLocation(any())).thenReturn(List.of(stockEntity));
        when(warehouseRepository.findAllById(any())).thenReturn(List.of(warehouse));
        when(locationRepository.findAllById(any())).thenReturn(List.of(location));
        when(stockLevelMapper.toResponse(stockEntity)).thenReturn(new StockLevelResponse(
                lowStockId,
                warehouseId,
                locationId,
                lowProductId,
                "",
                null,
                4,
                1,
                null,
                null));

        List<StockLevelExpandedResponse> result = stockLevelService.findLowStock();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(lowStockId);
        assertThat(result.get(0).qtyAvailable()).isEqualTo(3);
        assertThat(result.get(0).product().id()).isEqualTo(lowProductId);

        verify(stockLevelRepository).findByIdInWithWarehouseAndLocation(argThat(ids ->
                ids.size() == 1 && ids.contains(lowStockId)));
        verify(stockLevelRepository, never()).findAll();
    }

    @Test
    void adjustShouldNotApplyDeltaAgainWhenIdempotencyKeyAlreadyExists() {
        UUID warehouseId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        String idempotencyKey = "INBOUND_RECEIPT_ITEM:" + UUID.randomUUID() + ":ADJUST";

        Warehouse warehouse = Warehouse.builder()
                .id(warehouseId)
                .code("WH-02")
                .name("Secondary warehouse")
                .build();
        Location location = Location.builder()
                .id(locationId)
                .warehouse(warehouse)
                .code("FAST-B01")
                .zone("FAST")
                .build();
        StockLevel stockEntity = StockLevel.builder()
                .id(stockId)
                .warehouse(warehouse)
                .location(location)
                .productId(productId)
                .lotNumber("")
                .qtyOnHand(20)
                .qtyReserved(3)
                .build();

        when(stockMovementRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(StockMovement.builder()
                        .id(UUID.randomUUID())
                        .idempotencyKey(idempotencyKey)
                        .build()));
        when(stockLevelRepository.findByLocationIdAndProductIdAndLotNumber(locationId, productId, ""))
                .thenReturn(Optional.of(stockEntity));
        when(stockLevelMapper.toResponse(stockEntity)).thenReturn(new StockLevelResponse(
                stockId,
                warehouseId,
                locationId,
                productId,
                "",
                null,
                20,
                3,
                null,
                null));

        StockLevelResponse response = stockLevelService.adjust(new StockAdjustCommand(
                warehouseId,
                locationId,
                productId,
                null,
                5,
                idempotencyKey,
                "INBOUND_RECEIPT",
                UUID.randomUUID()));

        assertThat(response.qtyOnHand()).isEqualTo(20);
        assertThat(response.qtyAvailable()).isEqualTo(17);

        verify(stockLevelRepository, never()).save(any(StockLevel.class));
        verify(stockMovementRepository, never()).save(any());
        verify(warehouseRepository, never()).findById(any());
    }

    private static StockLevelRepository.StockQuantityView quantityView(
            UUID id, UUID productId, Integer qtyOnHand, Integer qtyReserved) {
        return new StockLevelRepository.StockQuantityView() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public UUID getProductId() {
                return productId;
            }

            @Override
            public Integer getQtyOnHand() {
                return qtyOnHand;
            }

            @Override
            public Integer getQtyReserved() {
                return qtyReserved;
            }
        };
    }
}
