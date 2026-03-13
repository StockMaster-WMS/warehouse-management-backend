package com.product_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.dto.request.CreateProductRequest;
import com.product_service.dto.response.ProductResponse;
import com.product_service.entity.Category;
import com.product_service.entity.Product;
import com.product_service.entity.Supplier;
import com.product_service.repository.CategoryRepository;
import com.product_service.repository.ProductRepository;
import com.product_service.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;

    public List<ProductResponse> findAll() {
        return productRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ProductResponse findById(UUID id) {
        return toResponse(getProduct(id));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Category not found"));

        Supplier supplier = null;
        if (request.primarySupplierId() != null) {
            supplier = supplierRepository.findById(request.primarySupplierId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Supplier not found"));
        }

        Product product = Product.builder()
                .sku(request.sku())
                .barcodeEan13(request.barcodeEan13())
                .name(request.name())
                .category(category)
                .primarySupplier(supplier)
                .baseUnit(request.baseUnit())
                .weightKg(request.weightKg())
                .lengthCm(request.lengthCm())
                .widthCm(request.widthCm())
                .heightCm(request.heightCm())
                .minStockQty(request.minStockQty() == null ? 0 : request.minStockQty())
                .isLotTracked(Boolean.TRUE.equals(request.isLotTracked()))
                .isExpiryTracked(Boolean.TRUE.equals(request.isExpiryTracked()))
                .status(request.status() == null || request.status().isBlank() ? "ACTIVE" : request.status())
                .createdBy(request.createdBy())
                .build();

        return toResponse(productRepository.save(product));
    }

    private Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getBarcodeEan13(),
                product.getName(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getPrimarySupplier() != null ? product.getPrimarySupplier().getId() : null,
                product.getBaseUnit(),
                product.getWeightKg(),
                product.getLengthCm(),
                product.getWidthCm(),
                product.getHeightCm(),
                product.getVolumeCm3(),
                product.getMinStockQty(),
                product.getIsLotTracked(),
                product.getIsExpiryTracked(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getCreatedBy()
        );
    }
}