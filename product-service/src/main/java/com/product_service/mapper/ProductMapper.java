package com.product_service.mapper;

import com.product_service.dto.request.CreateProductRequest;
import com.product_service.dto.request.UpdateProductRequest;
import com.product_service.dto.response.ProductResponse;
import com.product_service.entity.Product;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sku", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "primarySupplier", ignore = true)
    @Mapping(target = "volumeCm3", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(CreateProductRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "primarySupplier", ignore = true)
    @Mapping(target = "volumeCm3", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntity(UpdateProductRequest request, @MappingTarget Product product);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "primarySupplierId", source = "primarySupplier.id")
    ProductResponse toResponse(Product product);

    @AfterMapping
    default void setDefaultsOnCreate(CreateProductRequest request,
            @MappingTarget Product product) {
        applyDefaults(product);
    }

    @AfterMapping
    default void setDefaultsOnUpdate(UpdateProductRequest request,
            @MappingTarget Product product) {
        applyDefaults(product);
    }

    private static void applyDefaults(Product product) {
        if (product.getMinStockQty() == null) {
            product.setMinStockQty(0);
        }
        if (product.getIsLotTracked() == null) {
            product.setIsLotTracked(false);
        }
        if (product.getIsExpiryTracked() == null) {
            product.setIsExpiryTracked(false);
        }
        if (product.getStatus() == null || product.getStatus().isBlank()) {
            product.setStatus("ACTIVE");
        }
    }
}
