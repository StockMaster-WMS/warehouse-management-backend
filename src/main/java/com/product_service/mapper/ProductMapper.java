package com.product_service.mapper;

import com.auth_service.entity.UserAccount;
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
    @Mapping(target = "createdByUser", ignore = true)
    Product toEntity(CreateProductRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "primarySupplier", ignore = true)
    @Mapping(target = "volumeCm3", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdByUser", ignore = true)
    void updateEntity(UpdateProductRequest request, @MappingTarget Product product);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "primarySupplierId", source = "primarySupplier.id")
    @Mapping(target = "primarySupplierName", source = "primarySupplier.name")
    @Mapping(target = "createdByName", expression = "java(displayCreatedBy(product))")
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
        if (product.getIsFrozen() == null) {
            product.setIsFrozen(false);
        }
        if (product.getIsFragile() == null) {
            product.setIsFragile(false);
        }
        if (product.getIsHazmat() == null) {
            product.setIsHazmat(false);
        }
        if (product.getIsHeavy() == null) {
            product.setIsHeavy(false);
        }
        if (product.getStatus() == null || product.getStatus().isBlank()) {
            product.setStatus("ACTIVE");
        }
    }

    default String displayCreatedBy(Product product) {
        if (product == null) {
            return null;
        }
        if (product.getCreatedBy() != null
                && "00000000-0000-0000-0000-000000000000".equals(product.getCreatedBy().toString())) {
            return "Hệ thống";
        }
        UserAccount user = product.getCreatedByUser();
        if (user != null) {
            if (user.getFullName() != null && !user.getFullName().isBlank()) {
                return user.getFullName().trim();
            }
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                return user.getUsername().trim();
            }
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                return user.getEmail().trim();
            }
        }
        return null;
    }
}
