package com.warehouse_service.service;

import com.common.exception.AppException;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.mapper.LocationMapper;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.StockLevelRepository;
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
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private StockLevelRepository stockLevelRepository;

    @Mock
    private LocationMapper locationMapper;

    @InjectMocks
    private LocationService locationService;

    @Test
    void deleteShouldRejectLocationThatStillHasStock() {
        UUID locationId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .code("WH-01")
                .name("Main warehouse")
                .build();
        Location location = Location.builder()
                .id(locationId)
                .warehouse(warehouse)
                .code("A-01")
                .zone("FAST")
                .build();

        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(stockLevelRepository.existsByLocationId(locationId)).thenReturn(true);

        assertThatThrownBy(() -> locationService.delete(locationId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không thể xóa vị trí đang có tồn kho");

        verify(locationRepository, never()).delete(location);
    }
}
