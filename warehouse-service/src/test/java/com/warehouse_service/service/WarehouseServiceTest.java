package com.warehouse_service.service;

import com.common.exception.AppException;
import com.warehouse_service.dto.request.CreateWarehouseRequest;
import com.warehouse_service.dto.request.UpdateWarehouseRequest;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.WarehouseMapper;
import com.warehouse_service.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private WarehouseMapper warehouseMapper;

    @InjectMocks
    private WarehouseService warehouseService;

    @Test
    void createShouldRejectDuplicateCode() {
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "WH-01",
                "Main warehouse",
                null,
                null,
                true
        );

        when(warehouseRepository.existsByCode("WH-01")).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.create(request))
                .isInstanceOf(AppException.class)
                .hasMessage("Mã kho đã tồn tại");

        verify(warehouseMapper, never()).toEntity(request);
    }

    @Test
    void updateShouldRejectCodeOwnedByAnotherWarehouse() {
        UUID warehouseId = UUID.randomUUID();

        Warehouse current = Warehouse.builder()
                .id(warehouseId)
                .code("WH-01")
                .name("Main warehouse")
                .build();

        Warehouse conflicting = Warehouse.builder()
                .id(UUID.randomUUID())
                .code("WH-02")
                .name("Backup warehouse")
                .build();

        UpdateWarehouseRequest request = new UpdateWarehouseRequest(
                "WH-02",
                "Updated name",
                null,
                "Asia/Ho_Chi_Minh",
                true
        );

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(current));
        when(warehouseRepository.findByCode("WH-02")).thenReturn(Optional.of(conflicting));

        assertThatThrownBy(() -> warehouseService.update(warehouseId, request))
                .isInstanceOf(AppException.class)
                .hasMessage("Mã kho đã tồn tại");

        verify(warehouseMapper, never()).updateEntity(request, current);
        verify(warehouseRepository, never()).save(current);
    }

    @Test
    void updateShouldAllowKeepingOwnCode() {
        UUID warehouseId = UUID.randomUUID();

        Warehouse current = Warehouse.builder()
                .id(warehouseId)
                .code("WH-01")
                .name("Main warehouse")
                .build();

        UpdateWarehouseRequest request = new UpdateWarehouseRequest(
                "WH-01",
                "Updated warehouse",
                null,
                "Asia/Ho_Chi_Minh",
                true
        );

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(current));
        when(warehouseRepository.findByCode("WH-01")).thenReturn(Optional.of(current));
        when(warehouseRepository.save(current)).thenReturn(current);

        warehouseService.update(warehouseId, request);

        verify(warehouseMapper).updateEntity(request, current);
        verify(warehouseRepository).save(current);
    }
}