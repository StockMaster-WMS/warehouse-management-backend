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


    // Lấy danh sách danh mục có phân trang và bộ lọc.
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

    // Lấy chi tiết danh mục theo id.
    public CategoryResponse findById(UUID id) {
        return mapper.toResponse(get(id));
    }

    // Lấy chi tiết danh mục theo mã.
    public CategoryResponse findByCode(String code) {
        return mapper.toResponse(repository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy danh mục")));
    }


    // Tạo mới danh mục và sinh mã danh mục duy nhất.
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        Category parent = resolveParent(request.parentId());
        short level = computeLevel(parent);

        for (int i = 0; i < 10; i++) {
            String code = CodeGenerator.generate(PREFIX);
            Category entity = mapper.toEntity(request);
            String path = computePath(parent, entity.getName());

            entity.setCode(code);
            entity.setLevel(level);
            entity.setPath(path);
            entity.setParent(parent);

            try {
                return mapper.toResponse(repository.save(entity));
            } catch (DataIntegrityViolationException e) {
                if (!isDuplicateCode(e)) {
                    throw e;
                }
            }
        }

        throw new AppException(ErrorCode.BAD_REQUEST, "Không thể tạo mã duy nhất sau 10 lần thử");
    }

    // Kiểm tra lỗi vi phạm duy nhất có liên quan tới mã danh mục.
    private boolean isDuplicateCode(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("code");
    }


    // Cập nhật thông tin danh mục theo id.
    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        Category entity = get(id);

        Category parent = resolveParent(request.parentId());
        short level = computeLevel(parent);
        String path = computePath(parent, request.name());

        entity.setName(request.name());
        entity.setParent(parent);
        entity.setLevel(level);
        entity.setPath(path);
        entity.setIsActive(request.isActive());

        return mapper.toResponse(repository.save(entity));
    }

    // ================= DELETE =================

    // Xóa danh mục theo id.
    @Transactional
    public void delete(UUID id) {
        repository.delete(get(id));
    }

    // ================= HELPER =================

    // Tìm thực thể danh mục theo id.
    private Category get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy danh mục"));
    }

    // Resolve danh mục cha theo parentId.
    private Category resolveParent(UUID parentId) {
        return parentId == null ? null : get(parentId);
    }

    // Tính cấp độ danh mục dựa trên danh mục cha.
    private short computeLevel(Category parent) {
        return (short) (parent == null ? 0 : (parent.getLevel() == null ? 0 : parent.getLevel()) + 1);
    }

    // Tạo slug từ tên (không dấu, chữ thường, thay khoảng trắng bằng gạch ngang)
    private String toSlug(String input) {
        if (input == null) return "";
        // Chuyển Đ/đ thành d
        String temp = input.replace('Đ', 'D').replace('đ', 'd');
        // Loại bỏ dấu tiếng Việt
        String normalized = java.text.Normalizer.normalize(temp, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Chỉ giữ lại a-z, 0-9, dấu cách và gạch ngang, chuyển về thường, thay dấu cách bằng '-'
        return normalized
            .toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("[\\s]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    // Tính đường dẫn path của danh mục theo slug name
    private String computePath(Category parent, String name) {
        String slug = toSlug(name);
        if (parent == null) return slug;
        String parentPath = parent.getPath();
        return (parentPath == null || parentPath.isBlank()) ? slug : parentPath + "/" + slug;
    }
}