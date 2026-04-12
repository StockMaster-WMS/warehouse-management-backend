package com.outbound_service.mapper;

import com.outbound_service.dto.request.CreateCustomerRequest;
import com.outbound_service.dto.request.UpdateCustomerRequest;
import com.outbound_service.dto.response.CustomerResponse;
import com.outbound_service.entity.Customer;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Customer toEntity(CreateCustomerRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateCustomerRequest request, @MappingTarget Customer customer);

    CustomerResponse toResponse(Customer customer);

    @AfterMapping
    default void setDefaultsOnCreate(CreateCustomerRequest request, @MappingTarget Customer customer) {
        applyDefaults(customer);
    }

    @AfterMapping
    default void setDefaultsOnUpdate(UpdateCustomerRequest request, @MappingTarget Customer customer) {
        applyDefaults(customer);
    }

    private static void applyDefaults(Customer customer) {
        if (customer.getIsActive() == null) {
            customer.setIsActive(true);
        }
    }
}
