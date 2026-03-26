package com.warehouse_service.mapper;

import com.warehouse_service.dto.request.CreateWarehouseRequest;
import com.warehouse_service.dto.request.UpdateWarehouseRequest;
import com.warehouse_service.dto.response.WarehouseResponse;
import com.warehouse_service.entity.Warehouse;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface WarehouseMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "managerName", ignore = true)
    Warehouse toEntity(CreateWarehouseRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "managerName", ignore = true)
    void updateEntity(UpdateWarehouseRequest request, @MappingTarget Warehouse warehouse);

    WarehouseResponse toResponse(Warehouse warehouse);

    @AfterMapping
    default void setDefaultsOnCreate(CreateWarehouseRequest request,
            @MappingTarget Warehouse warehouse) {
        applyDefaults(warehouse);
    }

    @AfterMapping
    default void setDefaultsOnUpdate(UpdateWarehouseRequest request,
            @MappingTarget Warehouse warehouse) {
        applyDefaults(warehouse);
    }

    private static void applyDefaults(Warehouse warehouse) {
        if (warehouse.getTimezone() == null || warehouse.getTimezone().isBlank()) {
            warehouse.setTimezone("Asia/Ho_Chi_Minh");
        }
        if (warehouse.getIsActive() == null) {
            warehouse.setIsActive(true);
        }
    }
}
