package com.outbound_service.mapper;

import com.outbound_service.dto.request.CreatePickingItemRequest;
import com.outbound_service.dto.request.UpdatePickingItemRequest;
import com.outbound_service.dto.response.PickingItemResponse;
import com.outbound_service.entity.PickingItem;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface PickingItemMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "soItem", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "assigneeId", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    PickingItem toEntity(CreatePickingItemRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "soItem", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "assigneeId", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    void updateEntity(UpdatePickingItemRequest request, @MappingTarget PickingItem pickingItem);

    @Mapping(target = "soItemId", source = "soItem.id")
    @Mapping(target = "salesOrderNumber", source = "soItem.salesOrder.soNumber")
    @Mapping(target = "salesOrderPriority", source = "soItem.salesOrder.priority")
    @Mapping(target = "salesOrderCreatedAt", source = "soItem.salesOrder.createdAt")
    @Mapping(target = "warehouseId", source = "soItem.salesOrder.warehouseId")
    @Mapping(target = "productSku", source = "soItem.productSku")
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "barcodeEan13", ignore = true)
    @Mapping(target = "locationCode", ignore = true)
    @Mapping(target = "locationName", ignore = true)
    @Mapping(target = "warehouseCode", ignore = true)
    @Mapping(target = "warehouseName", ignore = true)
    PickingItemResponse toResponse(PickingItem pickingItem);

    @AfterMapping
    default void setDefaultsOnCreate(CreatePickingItemRequest request,
            @MappingTarget PickingItem pickingItem) {
        applyDefaults(pickingItem);
    }

    @AfterMapping
    default void setDefaultsOnUpdate(UpdatePickingItemRequest request,
            @MappingTarget PickingItem pickingItem) {
        applyDefaults(pickingItem);
    }

    private static void applyDefaults(PickingItem pickingItem) {
        if (pickingItem.getQtyPicked() == null) {
            pickingItem.setQtyPicked(0);
        }
        if (pickingItem.getLotNumber() == null) {
            pickingItem.setLotNumber("");
        }
    }
}
