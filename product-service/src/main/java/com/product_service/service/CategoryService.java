package com.product_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.util.CodeGenerator;
import com.product_service.dto.request.CreateCategoryRequest;
import com.product_service.dto.request.UpdateCategoryRequest;
import com.product_service.dto.response.CategoryResponse;
import com.product_service.entity.Category;
import com.product_service.mapper.CategoryMapper;
import com.product_service.repository.CategoryRepository;
import com.product_service.repository.CategorySpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private static final String PREFIX = "DM";

    private final CategoryRepository repository;
    private final CategoryMapper mapper;

    // ================= READ =================

    public PagedResponse<CategoryResponse> findAll(Pageable pageable, String keyword, Boolean isActive) {
        Specification<Category> spec = CategorySpecification.hasKeyword(keyword)
                .and(CategorySpecification.hasActive(isActive));
        Page<Category> page = repository.findAll(spec, pageable);
        Page<CategoryResponse> mapped = page.map(mapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    public CategoryResponse findById(UUID id) {
        return mapper.toResponse(get(id));
    }

    public CategoryResponse findByCode(String code) {
        return mapper.toResponse(repository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy danh mục")));
    }

    // ================= CREATE =================

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {

        for (int i = 0; i < 10; i++) {
            try {
                String code = CodeGenerator.generate(PREFIX);

                Category parent = resolveParent(request.parentId());
                short level = computeLevel(parent);
                String path = computePath(parent, code);

                Category entity = mapper.toEntity(request);
                entity.setCode(code);
                entity.setLevel(level);
                entity.setPath(path);
                entity.setParent(parent);

                return mapper.toResponse(repository.save(entity));

            } catch (DataIntegrityViolationException e) {
                // retry nếu trùng code
            }
        }

        throw new AppException(ErrorCode.BAD_REQUEST, "Không thể tạo mã duy nhất");
    }

    // ================= UPDATE =================

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        Category entity = get(id);

        // 🔥 giữ nguyên code
        String code = entity.getCode();

        Category parent = resolveParent(request.parentId());
        short level = computeLevel(parent);
        String path = computePath(parent, code);

        entity.setName(request.name());
        entity.setParent(parent);
        entity.setLevel(level);
        entity.setPath(path);
        entity.setIsActive(request.isActive());

        return mapper.toResponse(repository.save(entity));
    }

    // ================= DELETE =================

    @Transactional
    public void delete(UUID id) {
        repository.delete(get(id));
    }

    // ================= HELPER =================

    private Category get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy danh mục"));
    }

    private Category resolveParent(UUID parentId) {
        return parentId == null ? null : get(parentId);
    }

    private short computeLevel(Category parent) {
        return (short) (parent == null ? 0 : (parent.getLevel() == null ? 0 : parent.getLevel()) + 1);
    }

    private String computePath(Category parent, String code) {
        if (parent == null) return code;

        String parentPath = parent.getPath();
        return (parentPath == null || parentPath.isBlank())
                ? code
                : parentPath + "/" + code;
    }
}