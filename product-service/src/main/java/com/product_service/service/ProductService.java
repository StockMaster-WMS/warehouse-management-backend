package com.product_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.util.CodeGenerator;
import com.product_service.dto.request.CreateProductRequest;
import com.product_service.dto.request.UpdateProductRequest;
import com.product_service.dto.response.ProductResponse;
import com.product_service.entity.Category;
import com.product_service.entity.Product;
import com.product_service.entity.Supplier;
import com.product_service.mapper.ProductMapper;
import com.product_service.repository.CategoryRepository;
import com.product_service.repository.ProductRepository;
import com.product_service.repository.ProductSpecification;
import com.product_service.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private static final String PRODUCT_SKU_PREFIX = "SP";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final ProductMapper productMapper;

    public PagedResponse<ProductResponse> findAll(Pageable pageable, String keyword, UUID categoryId,
            String status) {
        Specification<Product> spec = ProductSpecification
                .hasKeyword(keyword)
                .and(ProductSpecification.hasCategory(categoryId))
                .and(ProductSpecification.hasStatus(status));
        Page<Product> page = productRepository.findAll(spec, pageable);
        Page<ProductResponse> mappedPage = page.map(productMapper::toResponse);
        return new com.common.api.PagedResponse<>(
                mappedPage.getContent(),
                mappedPage.getNumber(),
                mappedPage.getSize(),
                mappedPage.getTotalElements(),
                mappedPage.getTotalPages());
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
        validateBarcodeUniqueness(request.barcodeEan13(), null);

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy danh mục"));

        Supplier supplier = resolveSupplier(request.primarySupplierId());

        Product product = productMapper.toEntity(request);
        product.setSku(generateUniqueSku());
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

    private String generateUniqueSku() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String sku = CodeGenerator.generate(PRODUCT_SKU_PREFIX);
            if (!productRepository.existsBySku(sku)) {
                return sku;
            }
        }
        throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                "Không thể tạo mã SKU duy nhất, vui lòng thử lại");
    }

    private void validateUpdateRequest(UUID id, String sku, String barcodeEan13) {
        productRepository.findBySku(sku).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "SKU đã tồn tại");
            }
        });
        validateBarcodeUniqueness(barcodeEan13, id);
    }

    private void validateBarcodeUniqueness(String barcodeEan13, UUID productId) {
        if (barcodeEan13 == null || barcodeEan13.isBlank()) {
            return;
        }

        productRepository.findByBarcodeEan13(barcodeEan13).ifPresent(product -> {
            if (productId == null || !product.getId().equals(productId)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Mã vạch đã tồn tại");
            }
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
