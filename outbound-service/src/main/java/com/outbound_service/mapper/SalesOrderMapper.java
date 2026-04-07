package com.outbound_service.mapper;

import com.outbound_service.dto.request.CreateSalesOrderRequest;
import com.outbound_service.dto.request.UpdateSalesOrderRequest;
import com.outbound_service.dto.response.SalesOrderResponse;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderStatus;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SalesOrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "soNumber", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    SalesOrder toEntity(CreateSalesOrderRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(UpdateSalesOrderRequest request, @MappingTarget SalesOrder salesOrder);

    SalesOrderResponse toResponse(SalesOrder salesOrder);

    @AfterMapping
    default void setDefaultsOnCreate(CreateSalesOrderRequest request,
            @MappingTarget SalesOrder salesOrder) {
        applyDefaults(salesOrder);
    }

    @AfterMapping
    default void setDefaultsOnUpdate(UpdateSalesOrderRequest request,
            @MappingTarget SalesOrder salesOrder) {
        applyDefaults(salesOrder);
    }

    private static void applyDefaults(SalesOrder salesOrder) {
        if (salesOrder.getPriority() == null) {
            salesOrder.setPriority((short) 5);
        }
        if (salesOrder.getStatus() == null) {
            salesOrder.setStatus(SalesOrderStatus.DRAFT);
        }
    }
}
