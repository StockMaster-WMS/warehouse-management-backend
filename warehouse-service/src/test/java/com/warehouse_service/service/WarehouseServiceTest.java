package com.warehouse_service.service;

import com.common.exception.AppException;
import com.warehouse_service.dto.request.CreateWarehouseRequest;
import com.warehouse_service.dto.request.UpdateWarehouseRequest;
import com.warehouse_service.dto.response.WarehouseResponse;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.WarehouseMapper;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.repository.WarehouseRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private StockLevelRepository stockLevelRepository;

    @Mock
    private WarehouseMapper warehouseMapper;

    @InjectMocks
    private WarehouseService warehouseService;

    @Test
    void findAllShouldReturnPagedResponseWithFilters() {
        Pageable pageable = PageRequest.of(1, 2);

        Warehouse warehouseA = Warehouse.builder()
                .id(UUID.randomUUID())
                .code("WH-HCM-DC01")
                .name("Kho Tong HCM")
                .address("Quan 7")
                .timezone("Asia/Ho_Chi_Minh")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        Warehouse warehouseB = Warehouse.builder()
                .id(UUID.randomUUID())
                .code("WH-HCM-CFS02")
                .name("Kho CFS HCM")
                .address("Binh Chanh")
                .timezone("Asia/Ho_Chi_Minh")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        Page<Warehouse> page = new PageImpl<>(List.of(warehouseA, warehouseB), pageable, 5);

        when(warehouseRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(warehouseMapper.toResponse(warehouseA)).thenReturn(new WarehouseResponse(
                warehouseA.getId(),
                warehouseA.getCode(),
                warehouseA.getName(),
                warehouseA.getAddress(),
                null,
                warehouseA.getTimezone(),
                warehouseA.getIsActive(),
                warehouseA.getCreatedAt(),
                warehouseA.getCreatedAt()));
        when(warehouseMapper.toResponse(warehouseB)).thenReturn(new WarehouseResponse(
                warehouseB.getId(),
                warehouseB.getCode(),
                warehouseB.getName(),
                warehouseB.getAddress(),
                null,
                warehouseB.getTimezone(),
                warehouseB.getIsActive(),
                warehouseB.getCreatedAt(),
                warehouseB.getCreatedAt()));

        var result = warehouseService.findAll(pageable, "hcm", true, "Asia/Ho_Chi_Minh");

        Assertions.assertEquals(1, result.page());
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(5, result.totalElements());
        Assertions.assertEquals(3, result.totalPages());
        Assertions.assertEquals(2, result.content().size());

        verify(warehouseRepository).findAll(any(Specification.class), eq(pageable));
        verify(warehouseMapper).toResponse(warehouseA);
        verify(warehouseMapper).toResponse(warehouseB);
    }

    @Test
    void findAllShouldReturnEmptyPageWhenNoData() {
        Pageable pageable = PageRequest.of(0, 20);
        when(warehouseRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

        var result = warehouseService.findAll(pageable, null, null, null);

        Assertions.assertEquals(0, result.page());
        Assertions.assertEquals(20, result.size());
        Assertions.assertEquals(0, result.totalElements());
        Assertions.assertEquals(0, result.totalPages());
        Assertions.assertTrue(result.content().isEmpty());
    }

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
        when(warehouseMapper.toResponse(current)).thenReturn(new WarehouseResponse(
                current.getId(),
                current.getCode(),
                current.getName(),
                current.getAddress(),
                null,
                current.getTimezone(),
                current.getIsActive(),
                current.getCreatedAt(),
                current.getCreatedAt()));

        warehouseService.update(warehouseId, request);

        verify(warehouseMapper).updateEntity(request, current);
        verify(warehouseRepository).save(current);
    }
}
