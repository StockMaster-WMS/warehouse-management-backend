package com.product_service.mapper;

import com.product_service.dto.request.CreateCategoryRequest;
import com.product_service.dto.request.UpdateCategoryRequest;
import com.product_service.dto.response.CategoryResponse;
import com.product_service.entity.Category;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "path", ignore = true)
    @Mapping(target = "code", ignore = true)
    Category toEntity(CreateCategoryRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(UpdateCategoryRequest request, @MappingTarget Category category);

    @Mapping(target = "parentId", source = "parent.id")
    CategoryResponse toResponse(Category category);

    @AfterMapping
    default void setDefaultsOnCreate(CreateCategoryRequest request,
                                     @MappingTarget Category category) {
        applyDefaults(category);
    }

    @AfterMapping
    default void setDefaultsOnUpdate(UpdateCategoryRequest request,
                                     @MappingTarget Category category) {
        applyDefaults(category);
    }

    private static void applyDefaults(Category category) {
        if (category.getLevel() == null) {
            category.setLevel((short) 0);
        }
        if (category.getIsActive() == null) {
            category.setIsActive(true);
        }
    }
}
