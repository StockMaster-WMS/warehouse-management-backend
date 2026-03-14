package com.product_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.dto.request.CreateSupplierRequest;
import com.product_service.dto.request.UpdateSupplierRequest;
import com.product_service.dto.response.SupplierResponse;
import com.product_service.entity.Supplier;
import com.product_service.mapper.SupplierMapper;
import com.product_service.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;

    public List<SupplierResponse> findAll() {
        return supplierRepository.findAll()
                .stream()
                .map(supplierMapper::toResponse)
                .toList();
    }

    public SupplierResponse findById(UUID id) {
        return supplierMapper.toResponse(getSupplier(id));
    }

    public SupplierResponse findByCode(String code) {
        return supplierMapper.toResponse(supplierRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà cung cấp")));
    }

    @Transactional
    public SupplierResponse create(CreateSupplierRequest request) {
        validateCreateRequest(request.code(), request.taxCode());

        Supplier supplier = supplierMapper.toEntity(request);

        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(UUID id, UpdateSupplierRequest request) {
        Supplier supplier = getSupplier(id);
        validateUpdateRequest(id, request.code(), request.taxCode());

        supplierMapper.updateEntity(request, supplier);

        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public void delete(UUID id) {
        Supplier supplier = getSupplier(id);
        supplierRepository.delete(supplier);
    }

    private Supplier getSupplier(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà cung cấp"));
    }

    private void validateCreateRequest(String code, String taxCode) {
        if (supplierRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã nhà cung cấp đã tồn tại");
        }
        validateTaxCodeUniqueness(taxCode, null);
    }

    private void validateUpdateRequest(UUID id, String code, String taxCode) {
        supplierRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã nhà cung cấp đã tồn tại");
                });

        validateTaxCodeUniqueness(taxCode, id);
    }

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

        supplierRepository.findAll().stream()
                .filter(supplier -> taxCode.equals(supplier.getTaxCode()))
                .filter(supplier -> !supplier.getId().equals(supplierId))
                .findFirst()
                .ifPresent(supplier -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã số thuế đã tồn tại");
                });
    }

}
