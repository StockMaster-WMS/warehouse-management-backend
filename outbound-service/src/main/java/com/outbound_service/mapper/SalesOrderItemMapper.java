package com.outbound_service.mapper;

import com.outbound_service.dto.request.CreateSalesOrderItemRequest;
import com.outbound_service.dto.request.UpdateSalesOrderItemRequest;
import com.outbound_service.dto.response.SalesOrderItemResponse;
import com.outbound_service.entity.SalesOrderItem;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SalesOrderItemMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "salesOrder", ignore = true)
    @Mapping(target = "shippedQty", ignore = true)
    SalesOrderItem toEntity(CreateSalesOrderItemRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "salesOrder", ignore = true)
    @Mapping(target = "shippedQty", ignore = true)
    void updateEntity(UpdateSalesOrderItemRequest request, @MappingTarget SalesOrderItem item);

    @Mapping(target = "salesOrderId", source = "salesOrder.id")
    SalesOrderItemResponse toResponse(SalesOrderItem item);

    @AfterMapping
    default void defaultShippedOnCreate(CreateSalesOrderItemRequest request, @MappingTarget SalesOrderItem item) {
        if (item.getShippedQty() == null) {
            item.setShippedQty(0);
        }
    }
}
