package com.product_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.dto.request.CreateCategoryRequest;
import com.product_service.dto.request.UpdateCategoryRequest;
import com.product_service.dto.response.CategoryResponse;
import com.product_service.entity.Category;
import com.product_service.mapper.CategoryMapper;
import com.product_service.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    public CategoryResponse findById(UUID id) {
        return categoryMapper.toResponse(getCategory(id));
    }

    public CategoryResponse findByCode(String code) {
        return categoryMapper.toResponse(categoryRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy danh mục")));
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        if (categoryRepository.existsByCode(request.code())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã danh mục đã tồn tại");
        }

        Category parent = resolveParent(request.parentId());
        Category category = categoryMapper.toEntity(request);
        category.setParent(parent);

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        Category category = getCategory(id);

        categoryRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã danh mục đã tồn tại");
                });

        Category parent = resolveParent(request.parentId());
        categoryMapper.updateEntity(request, category);
        category.setParent(parent);

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        Category category = getCategory(id);
        categoryRepository.delete(category);
    }

    private Category getCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy danh mục"));
    }

    private Category resolveParent(UUID parentId) {
        if (parentId == null) {
            return null;
        }
        return getCategory(parentId);
    }

}
