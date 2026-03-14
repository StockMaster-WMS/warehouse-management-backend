package com.outbound_service.mapper;

import com.outbound_service.dto.request.CreatePickingItemRequest;
import com.outbound_service.dto.request.UpdatePickingItemRequest;
import com.outbound_service.dto.response.PickingItemResponse;
import com.outbound_service.entity.PickingItem;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PickingItemMapper {

    @Mapping(target = "id", ignore = true)
    PickingItem toEntity(CreatePickingItemRequest request);

    @Mapping(target = "id", ignore = true)
    void updateEntity(UpdatePickingItemRequest request, @MappingTarget PickingItem pickingItem);

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
    }
}