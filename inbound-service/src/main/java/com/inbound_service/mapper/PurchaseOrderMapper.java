package com.inbound_service.mapper;

import com.inbound_service.dto.request.CreatePurchaseOrderRequest;
import com.inbound_service.dto.request.UpdatePurchaseOrderRequest;
import com.inbound_service.dto.response.PurchaseOrderResponse;
import com.inbound_service.entity.PurchaseOrder;
import com.inbound_service.entity.PurchaseOrderStatus;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.math.BigDecimal;

@Mapper(componentModel = "default")
public interface PurchaseOrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    PurchaseOrder toEntity(CreatePurchaseOrderRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(UpdatePurchaseOrderRequest request, @MappingTarget PurchaseOrder purchaseOrder);

    PurchaseOrderResponse toResponse(PurchaseOrder purchaseOrder);

    @AfterMapping
    default void setDefaultsOnCreate(CreatePurchaseOrderRequest request,
                                     @MappingTarget PurchaseOrder purchaseOrder) {
        applyDefaults(purchaseOrder);
    }

    @AfterMapping
    default void setDefaultsOnUpdate(UpdatePurchaseOrderRequest request,
                                     @MappingTarget PurchaseOrder purchaseOrder) {
        applyDefaults(purchaseOrder);
    }

    private static void applyDefaults(PurchaseOrder purchaseOrder) {
        if (purchaseOrder.getStatus() == null) {
            purchaseOrder.setStatus(PurchaseOrderStatus.DRAFT);
        }
        if (purchaseOrder.getTotalAmount() == null) {
            purchaseOrder.setTotalAmount(BigDecimal.ZERO);
        }
    }
}
