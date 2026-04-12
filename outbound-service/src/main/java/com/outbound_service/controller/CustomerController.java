package com.outbound_service.controller;

import com.common.api.ApiResponse;
import com.common.api.PagedResponse;
import com.outbound_service.dto.request.CreateCustomerRequest;
import com.outbound_service.dto.request.UpdateCustomerRequest;
import com.outbound_service.dto.response.CustomerResponse;
import com.outbound_service.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customers")
@Tag(name = "Customer APIs", description = "Quản lý khách hàng")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @Operation(summary = "Lấy danh sách khách hàng", description = "Phân trang và lọc theo keyword, trạng thái")
    public ApiResponse<PagedResponse<CustomerResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sort));
        return ApiResponse.success("Lấy danh sách khách hàng thành công",
                customerService.findAll(pageable, keyword, isActive));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy khách hàng theo ID")
    public ApiResponse<CustomerResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Lấy khách hàng thành công", customerService.findById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Lấy khách hàng theo mã")
    public ApiResponse<CustomerResponse> getByCode(@PathVariable String code) {
        return ApiResponse.success("Lấy khách hàng thành công", customerService.findByCode(code));
    }

    @PostMapping
    @Operation(summary = "Tạo khách hàng")
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ApiResponse.success("Tạo khách hàng thành công", customerService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật khách hàng")
    public ApiResponse<CustomerResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return ApiResponse.success("Cập nhật khách hàng thành công", customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa khách hàng")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ApiResponse.success("Xóa khách hàng thành công", id.toString());
    }
}
