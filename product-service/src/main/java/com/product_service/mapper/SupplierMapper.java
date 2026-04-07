package com.product_service.mapper;

import com.product_service.dto.request.CreateSupplierRequest;
import com.product_service.dto.request.UpdateSupplierRequest;
import com.product_service.dto.response.SupplierResponse;
import com.product_service.entity.Supplier;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SupplierMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Supplier toEntity(CreateSupplierRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateSupplierRequest request, @MappingTarget Supplier supplier);

    SupplierResponse toResponse(Supplier supplier);

    @AfterMapping
    default void setDefaultsOnCreate(CreateSupplierRequest request,
                                     @MappingTarget Supplier supplier) {
        applyDefaults(supplier);
    }

    @AfterMapping
    default void setDefaultsOnUpdate(UpdateSupplierRequest request,
                                     @MappingTarget Supplier supplier) {
        applyDefaults(supplier);
    }

    private static void applyDefaults(Supplier supplier) {
        if (supplier.getPaymentTerms() == null) {
            supplier.setPaymentTerms((short) 30);
        }
        if (supplier.getLeadTimeDays() == null) {
            supplier.setLeadTimeDays((short) 7);
        }
        if (supplier.getStatus() == null || supplier.getStatus().isBlank()) {
            supplier.setStatus("active");
        }
    }
}
