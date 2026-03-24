package com.warehouse_service.mapper;

import com.warehouse_service.dto.request.CreateStockLevelRequest;
import com.warehouse_service.dto.request.UpdateStockLevelRequest;
import com.warehouse_service.dto.response.StockLevelResponse;
import com.warehouse_service.entity.StockLevel;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "default")
public interface StockLevelMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "qtyAvailable", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    StockLevel toEntity(CreateStockLevelRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "qtyAvailable", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateStockLevelRequest request, @MappingTarget StockLevel stockLevel);

    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "locationId", source = "location.id")
    StockLevelResponse toResponse(StockLevel stockLevel);

    @AfterMapping
    default void setDefaultsOnCreate(CreateStockLevelRequest request,
            @MappingTarget StockLevel stockLevel) {
        applyDefaults(stockLevel);
    }

    @AfterMapping
    default void setDefaultsOnUpdate(UpdateStockLevelRequest request,
            @MappingTarget StockLevel stockLevel) {
        applyDefaults(stockLevel);
    }

    private static void applyDefaults(StockLevel stockLevel) {
        if (stockLevel.getLotNumber() == null || stockLevel.getLotNumber().isBlank()) {
            stockLevel.setLotNumber("");
        }
        if (stockLevel.getQtyReserved() == null) {
            stockLevel.setQtyReserved(0);
        }
    }
}