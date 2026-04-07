package com.product_service.service;

import com.common.exception.AppException;
import com.product_service.client.InboundClient;
import com.product_service.dto.request.UpdateSupplierRequest;
import com.product_service.entity.Supplier;
import com.product_service.mapper.SupplierMapper;
import com.product_service.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierMapper supplierMapper;

    @Mock
    private InboundClient inboundClient;

    @InjectMocks
    private SupplierService supplierService;

    @Test
    void updateShouldRejectTaxCodeOwnedByAnotherSupplier() {
        UUID supplierId = UUID.randomUUID();

        Supplier existingSupplier = Supplier.builder()
                .id(supplierId)
                .code("NCC-001")
                .taxCode("0312345678")
                .build();

        Supplier conflictingSupplier = Supplier.builder()
                .id(UUID.randomUUID())
                .taxCode("0999999999")
                .build();

        UpdateSupplierRequest request = new UpdateSupplierRequest(
                "NCC-001",
                "Updated supplier",
                "0999999999",
                null,
                null,
                null,
                null,
                null,
                null,
                "active"
        );

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(existingSupplier));
        when(supplierRepository.findByCode("NCC-001")).thenReturn(Optional.of(existingSupplier));
        when(supplierRepository.findByTaxCode("0999999999")).thenReturn(Optional.of(conflictingSupplier));

        assertThatThrownBy(() -> supplierService.update(supplierId, request))
                .isInstanceOf(AppException.class)
                .hasMessage("Mã số thuế đã tồn tại");

        verify(supplierRepository, never()).save(existingSupplier);
    }

    @Test
    void updateShouldAllowKeepingSameTaxCodeForCurrentSupplier() {
        UUID supplierId = UUID.randomUUID();

        Supplier existingSupplier = Supplier.builder()
                .id(supplierId)
                .code("NCC-001")
                .taxCode("0312345678")
                .build();

        UpdateSupplierRequest request = new UpdateSupplierRequest(
                "NCC-001",
                "Updated supplier",
                "0312345678",
                null,
                null,
                null,
                null,
                null,
                null,
                "active"
        );

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(existingSupplier));
        when(supplierRepository.findByCode("NCC-001")).thenReturn(Optional.of(existingSupplier));
        when(supplierRepository.findByTaxCode("0312345678")).thenReturn(Optional.of(existingSupplier));
        when(supplierRepository.save(existingSupplier)).thenReturn(existingSupplier);

        supplierService.update(supplierId, request);

        verify(supplierRepository).save(existingSupplier);
    }
}
