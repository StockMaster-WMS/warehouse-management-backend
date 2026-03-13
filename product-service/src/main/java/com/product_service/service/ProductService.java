package com.product_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.dto.request.CreateProductRequest;
import com.product_service.dto.request.UpdateProductRequest;
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

    public ProductResponse findBySku(String sku) {
        return toResponse(productRepository.findBySku(sku)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found")));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        validateCreateRequest(request.sku(), request.barcodeEan13());

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Category not found"));

        Product product = Product.builder()
                .sku(request.sku())
                .category(category)
                .createdBy(request.createdBy())
                .build();

        applyRequest(product, request.barcodeEan13(), request.name(), category, request.primarySupplierId(),
                request.baseUnit(), request.weightKg(), request.lengthCm(), request.widthCm(), request.heightCm(),
                request.minStockQty(), request.isLotTracked(), request.isExpiryTracked(), request.status());

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = getProduct(id);
        validateUpdateRequest(id, request.sku(), request.barcodeEan13());

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Category not found"));

        product.setSku(request.sku());
        applyRequest(product, request.barcodeEan13(), request.name(), category, request.primarySupplierId(),
                request.baseUnit(), request.weightKg(), request.lengthCm(), request.widthCm(), request.heightCm(),
                request.minStockQty(), request.isLotTracked(), request.isExpiryTracked(), request.status());

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(UUID id) {
        Product product = getProduct(id);
        productRepository.delete(product);
    }

    private Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
    }

    private void validateCreateRequest(String sku, String barcodeEan13) {
        if (productRepository.existsBySku(sku)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "SKU already exists");
        }
        validateBarcodeUniqueness(barcodeEan13, null);
    }

    private void validateUpdateRequest(UUID id, String sku, String barcodeEan13) {
        productRepository.findBySku(sku)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "SKU already exists");
                });
        validateBarcodeUniqueness(barcodeEan13, id);
    }

    private void validateBarcodeUniqueness(String barcodeEan13, UUID productId) {
        if (barcodeEan13 == null || barcodeEan13.isBlank()) {
            return;
        }

        if (productId == null) {
            if (productRepository.existsByBarcodeEan13(barcodeEan13)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Barcode already exists");
            }
            return;
        }

        productRepository.findAll().stream()
                .filter(product -> barcodeEan13.equals(product.getBarcodeEan13()))
                .filter(product -> !product.getId().equals(productId))
                .findFirst()
                .ifPresent(product -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Barcode already exists");
                });
    }

    private void applyRequest(Product product,
                              String barcodeEan13,
                              String name,
                              Category category,
                              UUID primarySupplierId,
                              String baseUnit,
                              java.math.BigDecimal weightKg,
                              java.math.BigDecimal lengthCm,
                              java.math.BigDecimal widthCm,
                              java.math.BigDecimal heightCm,
                              Integer minStockQty,
                              Boolean isLotTracked,
                              Boolean isExpiryTracked,
                              String status) {
        Supplier supplier = null;
        if (primarySupplierId != null) {
            supplier = supplierRepository.findById(primarySupplierId)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Supplier not found"));
        }

        product.setBarcodeEan13(barcodeEan13);
        product.setName(name);
        product.setCategory(category);
        product.setPrimarySupplier(supplier);
        product.setBaseUnit(baseUnit);
        product.setWeightKg(weightKg);
        product.setLengthCm(lengthCm);
        product.setWidthCm(widthCm);
        product.setHeightCm(heightCm);
        product.setMinStockQty(minStockQty == null ? 0 : minStockQty);
        product.setIsLotTracked(Boolean.TRUE.equals(isLotTracked));
        product.setIsExpiryTracked(Boolean.TRUE.equals(isExpiryTracked));
        product.setStatus(status == null || status.isBlank() ? "ACTIVE" : status);
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