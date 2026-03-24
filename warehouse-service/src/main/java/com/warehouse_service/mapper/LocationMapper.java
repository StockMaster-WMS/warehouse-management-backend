package com.warehouse_service.mapper;

import com.warehouse_service.dto.request.CreateLocationRequest;
import com.warehouse_service.dto.request.UpdateLocationRequest;
import com.warehouse_service.dto.response.LocationResponse;
import com.warehouse_service.entity.Location;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "default")
public interface LocationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Location toEntity(CreateLocationRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(UpdateLocationRequest request, @MappingTarget Location location);

    @Mapping(target = "warehouseId", source = "warehouse.id")
    LocationResponse toResponse(Location location);

    @AfterMapping
    default void setDefaultsOnCreate(CreateLocationRequest request,
            @MappingTarget Location location) {
        applyDefaults(location);
    }

    @AfterMapping
    default void setDefaultsOnUpdate(UpdateLocationRequest request,
            @MappingTarget Location location) {
        applyDefaults(location);
    }

    private static void applyDefaults(Location location) {
        if (location.getLocationType() == null || location.getLocationType().isBlank()) {
            location.setLocationType("STORAGE");
        }
        if (location.getStatus() == null || location.getStatus().isBlank()) {
            location.setStatus("AVAILABLE");
        }
        if (location.getIsActive() == null) {
            location.setIsActive(true);
        }
    }
}