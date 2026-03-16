package com.inbound_service.mapper;

import com.inbound_service.dto.request.CreatePoItemRequest;
import com.inbound_service.dto.request.UpdatePoItemRequest;
import com.inbound_service.dto.response.PoItemResponse;
import com.inbound_service.entity.PoItem;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PoItemMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "purchaseOrder", ignore = true)
    PoItem toEntity(CreatePoItemRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "purchaseOrder", ignore = true)
    void updateEntity(UpdatePoItemRequest request, @MappingTarget PoItem poItem);

    @Mapping(target = "purchaseOrderId", source = "purchaseOrder.id")
    PoItemResponse toResponse(PoItem poItem);

    @AfterMapping
    default void setDefaultsOnCreate(CreatePoItemRequest request, @MappingTarget PoItem poItem) {
        applyDefaults(poItem);
    }

    @AfterMapping
    default void setDefaultsOnUpdate(UpdatePoItemRequest request, @MappingTarget PoItem poItem) {
        applyDefaults(poItem);
    }

    private static void applyDefaults(PoItem poItem) {
        if (poItem.getReceivedQty() == null) {
            poItem.setReceivedQty(0);
        }
    }
}