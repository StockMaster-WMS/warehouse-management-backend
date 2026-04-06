package com.product_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.dto.request.CreateSupplierRequest;
import com.product_service.dto.request.UpdateSupplierRequest;
import com.product_service.dto.response.SupplierResponse;
import com.product_service.entity.Supplier;
import com.product_service.mapper.SupplierMapper;
import com.product_service.repository.SupplierRepository;
import com.product_service.repository.SupplierSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {

    private static final Set<String> VALID_STATUSES = Set.of("active", "inactive", "suspended");

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;

    // Lấy danh sách nhà cung cấp có phân trang và bộ lọc.
    public PagedResponse<SupplierResponse> findAll(Pageable pageable, String keyword, String status) {
        Specification<Supplier> spec = SupplierSpecification.hasKeyword(keyword)
                .and(SupplierSpecification.hasStatus(status));
        Page<Supplier> page = supplierRepository.findAll(spec, pageable);
        Page<SupplierResponse> mapped = page.map(supplierMapper::toResponse);
        return new PagedResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    // Lấy chi tiết nhà cung cấp theo id.
    public SupplierResponse findById(UUID id) {
        return supplierMapper.toResponse(getSupplier(id));
    }

    // Lấy chi tiết nhà cung cấp theo mã.
    public SupplierResponse findByCode(String code) {
        return supplierMapper.toResponse(supplierRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà cung cấp")));
    }

    // Tạo mới nhà cung cấp.
    @Transactional
    public SupplierResponse create(CreateSupplierRequest request) {
        validateCreateRequest(request.code(), request.taxCode());

        Supplier supplier = supplierMapper.toEntity(request);

        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    // Cập nhật nhà cung cấp theo id.
    @Transactional
    public SupplierResponse update(UUID id, UpdateSupplierRequest request) {
        Supplier supplier = getSupplier(id);
        validateUpdateRequest(id, request.code(), request.taxCode());

        supplierMapper.updateEntity(request, supplier);

        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    // Xóa nhà cung cấp theo id.
    @Transactional
    public SupplierResponse changeStatus(UUID id, String status) {
        String normalized = status.toLowerCase();
        if (!VALID_STATUSES.contains(normalized)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Trạng thái không hợp lệ. Chỉ chấp nhận: " + VALID_STATUSES);
        }
        Supplier supplier = getSupplier(id);
        supplier.setStatus(normalized);
        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public void delete(UUID id) {
        Supplier supplier = getSupplier(id);
        supplierRepository.delete(supplier);
    }

    // Tìm thực thể nhà cung cấp theo id.
    private Supplier getSupplier(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà cung cấp"));
    }

    // Kiểm tra hợp lệ khi tạo mới nhà cung cấp.
    private void validateCreateRequest(String code, String taxCode) {
        if (supplierRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã nhà cung cấp đã tồn tại");
        }
        validateTaxCodeUniqueness(taxCode, null);
    }

    // Kiểm tra hợp lệ khi cập nhật nhà cung cấp.
    private void validateUpdateRequest(UUID id, String code, String taxCode) {
        supplierRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã nhà cung cấp đã tồn tại");
                });

        validateTaxCodeUniqueness(taxCode, id);
    }

    // Kiểm tra mã số thuế là duy nhất.
    private void validateTaxCodeUniqueness(String taxCode, UUID supplierId) {
        if (taxCode == null || taxCode.isBlank()) {
            return;
        }

        if (supplierId == null) {
            if (supplierRepository.existsByTaxCode(taxCode)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Mã số thuế đã tồn tại");
            }
            return;
        }

        supplierRepository.findByTaxCode(taxCode)
                .filter(supplier -> !supplier.getId().equals(supplierId))
                .ifPresent(supplier -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã số thuế đã tồn tại");
                });
    }

}
