package com.product_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.common.util.CodeGenerator;
import com.product_service.dto.request.CreateProductRequest;
import com.product_service.dto.request.UpdateProductRequest;
import com.product_service.dto.response.ProductResponse;
import com.product_service.dto.response.ProductSummaryResponse;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    // Lấy danh sách sản phẩm có phân trang và bộ lọc.
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

    // Lấy chi tiết sản phẩm theo id.
    public ProductResponse findById(UUID id) {
        return productMapper.toResponse(getProduct(id));
    }

    // Lấy chi tiết sản phẩm theo SKU.
    public ProductResponse findBySku(String sku) {
        return productMapper.toResponse(productRepository.findBySku(sku)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm")));
    }

    // Tìm sản phẩm theo tên (không phân biệt hoa/thường).
    public ProductResponse findByName(String name) {
        return productMapper.toResponse(productRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm")));
    }

    // Lấy danh sách tóm tắt sản phẩm theo danh sách id.
    public List<ProductSummaryResponse> findSummariesByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        // Deduplicate to avoid large IN lists & repeated mapping
        Set<UUID> unique = new HashSet<>(ids);
        List<Product> products = productRepository.findAllById(unique);

        List<ProductSummaryResponse> result = new ArrayList<>(products.size());
        for (Product p : products) {
            result.add(new ProductSummaryResponse(
                    p.getId(),
                    p.getSku(),
                    p.getName(),
                    p.getMinStockQty()
            ));
        }
        return result;
    }

    // Tạo mới sản phẩm và sinh SKU duy nhất.
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

    // Cập nhật thông tin sản phẩm theo id.
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

    // Xóa sản phẩm theo id.
    @Transactional
    public void delete(UUID id) {
        Product product = getProduct(id);
        productRepository.delete(product);
    }

    // Tìm thực thể sản phẩm, ném lỗi nếu không tồn tại.
    private Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sản phẩm"));
    }

    // Sinh SKU mới và kiểm tra trùng lặp.
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

    // Kiểm tra hợp lệ khi cập nhật SKU và barcode.
    private void validateUpdateRequest(UUID id, String sku, String barcodeEan13) {
        productRepository.findBySku(sku).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "SKU đã tồn tại");
            }
        });
        validateBarcodeUniqueness(barcodeEan13, id);
    }

    // Kiểm tra barcode là duy nhất trong hệ thống.
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

    // Resolve nhà cung cấp chính theo id.
    private Supplier resolveSupplier(UUID primarySupplierId) {
        if (primarySupplierId == null) {
            return null;
        }

        return supplierRepository.findById(primarySupplierId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà cung cấp"));
    }

}
