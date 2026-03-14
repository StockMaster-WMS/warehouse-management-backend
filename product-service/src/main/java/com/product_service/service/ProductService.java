package com.product_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.product_service.dto.request.CreateProductRequest;
import com.product_service.dto.request.UpdateProductRequest;
import com.product_service.dto.response.ProductResponse;
import com.product_service.entity.Category;
import com.product_service.entity.Product;
import com.product_service.entity.Supplier;
import com.product_service.mapper.ProductMapper;
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
    private final ProductMapper productMapper;

    public List<ProductResponse> findAll() {
        return productRepository.findAll()
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    public ProductResponse findById(UUID id) {
        return productMapper.toResponse(getProduct(id));
    }

    public ProductResponse findBySku(String sku) {
        return productMapper.toResponse(productRepository.findBySku(sku)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm")));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        validateCreateRequest(request.sku(), request.barcodeEan13());

        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy danh mục"));

        Supplier supplier = resolveSupplier(request.primarySupplierId());

        Product product = productMapper.toEntity(request);
        product.setCategory(category);
        product.setPrimarySupplier(supplier);

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = getProduct(id);
        validateUpdateRequest(id, request.sku(), request.barcodeEan13());

        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy danh mục"));

        Supplier supplier = resolveSupplier(request.primarySupplierId());

        productMapper.updateEntity(request, product);
        product.setCategory(category);
        product.setPrimarySupplier(supplier);

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(UUID id) {
        Product product = getProduct(id);
        productRepository.delete(product);
    }

    private Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm"));
    }

    private void validateCreateRequest(String sku, String barcodeEan13) {
        if (productRepository.existsBySku(sku)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "SKU đã tồn tại");
        }
        validateBarcodeUniqueness(barcodeEan13, null);
    }

    private void validateUpdateRequest(UUID id, String sku, String barcodeEan13) {
        productRepository.findBySku(sku)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "SKU đã tồn tại");
                });
        validateBarcodeUniqueness(barcodeEan13, id);
    }

    private void validateBarcodeUniqueness(String barcodeEan13, UUID productId) {
        if (barcodeEan13 == null || barcodeEan13.isBlank()) {
            return;
        }

        if (productId == null) {
            if (productRepository.existsByBarcodeEan13(barcodeEan13)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Mã vạch đã tồn tại");
            }
            return;
        }

        productRepository.findByBarcodeEan13(barcodeEan13)
                .filter(product -> !product.getId().equals(productId))
                .ifPresent(product -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã vạch đã tồn tại");
                });
    }

    private Supplier resolveSupplier(UUID primarySupplierId) {
        if (primarySupplierId == null) {
            return null;
        }

        return supplierRepository.findById(primarySupplierId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà cung cấp"));
    }

}