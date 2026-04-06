package com.product_service.service;

import com.common.exception.AppException;
import com.product_service.dto.request.UpdateProductRequest;
import com.product_service.entity.Category;
import com.product_service.entity.Product;
import com.product_service.mapper.ProductMapper;
import com.product_service.repository.CategoryRepository;
import com.product_service.repository.ProductRepository;
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
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    void updateShouldRejectBarcodeOwnedByAnotherProduct() {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Product existingProduct = Product.builder()
                .id(productId)
                .sku("SP-001")
                .barcodeEan13("8938505974192")
                .build();

        Product conflictingProduct = Product.builder()
                .id(UUID.randomUUID())
                .barcodeEan13("8938505974000")
                .build();

        UpdateProductRequest request = new UpdateProductRequest(
                "SP-001",
                "8938505974000",
                "Updated product",
                categoryId,
                null,
                "box",
                null,
                null,
                null,
                null,
                "ACTIVE"
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(productRepository.findBySku("SP-001")).thenReturn(Optional.of(existingProduct));
        when(productRepository.findByBarcodeEan13("8938505974000")).thenReturn(Optional.of(conflictingProduct));

        assertThatThrownBy(() -> productService.update(productId, request))
                .isInstanceOf(AppException.class)
                .hasMessage("Mã vạch đã tồn tại");

        verify(categoryRepository, never()).findById(categoryId);
        verify(productRepository, never()).save(existingProduct);
    }

    @Test
    void updateShouldAllowKeepingSameBarcodeForCurrentProduct() {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Product existingProduct = Product.builder()
                .id(productId)
                .sku("SP-001")
                .barcodeEan13("8938505974192")
                .build();

        Category category = Category.builder()
                .id(categoryId)
                .code("FOOD")
                .name("Food")
                .build();

        UpdateProductRequest request = new UpdateProductRequest(
                "SP-001",
                "8938505974192",
                "Updated product",
                categoryId,
                null,
                "box",
                null,
                null,
                null,
                null,
                "ACTIVE"
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(productRepository.findBySku("SP-001")).thenReturn(Optional.of(existingProduct));
        when(productRepository.findByBarcodeEan13("8938505974192")).thenReturn(Optional.of(existingProduct));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.save(existingProduct)).thenReturn(existingProduct);

        productService.update(productId, request);

        verify(categoryRepository).findById(categoryId);
        verify(productRepository).save(existingProduct);
    }
}
