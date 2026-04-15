package com.outbound_service.service;

import com.common.api.PagedResponse;
import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.outbound_service.dto.request.CreateCustomerRequest;
import com.outbound_service.dto.request.UpdateCustomerRequest;
import com.outbound_service.dto.response.CustomerResponse;
import com.outbound_service.entity.Customer;
import com.outbound_service.mapper.CustomerMapper;
import com.outbound_service.repository.CustomerRepository;
import com.outbound_service.repository.CustomerSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public PagedResponse<CustomerResponse> findAll(Pageable pageable, String keyword, Boolean isActive) {
        Specification<Customer> spec = CustomerSpecification.hasKeyword(keyword)
                .and(CustomerSpecification.hasActive(isActive));
        Page<Customer> page = customerRepository.findAll(spec, pageable);
        return new PagedResponse<>(
                page.map(customerMapper::toResponse).getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public CustomerResponse findById(UUID id) {
        return customerMapper.toResponse(getCustomer(id));
    }

    public CustomerResponse findByCode(String code) {
        String normalizedCode = normalizeCode(code);
        return customerMapper.toResponse(customerRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khách hàng")));
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        String normalizedCode = normalizeCode(request.code());
        if (customerRepository.existsByCode(normalizedCode)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã khách hàng đã tồn tại");
        }

        Customer customer = customerMapper.toEntity(request);
        normalizeCustomer(customer);
        Customer saved = customerRepository.save(customer);
        return customerMapper.toResponse(saved);
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        Customer customer = getCustomer(id);
        String normalizedCode = normalizeCode(request.code());

        customerRepository.findByCode(normalizedCode)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã khách hàng đã tồn tại");
                });

        customerMapper.updateEntity(request, customer);
        normalizeCustomer(customer);
        customer.setCode(normalizedCode);

        Customer saved = customerRepository.save(customer);
        return customerMapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Customer customer = getCustomer(id);
        customerRepository.delete(customer);
    }

    private Customer getCustomer(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khách hàng"));
    }

    private void normalizeCustomer(Customer customer) {
        customer.setCode(normalizeCode(customer.getCode()));
        customer.setName(normalizeText(customer.getName()));
        customer.setContactName(normalizeText(customer.getContactName()));
        customer.setPhone(normalizeText(customer.getPhone()));
        customer.setEmail(normalizeText(customer.getEmail()));
        if (StringUtils.hasText(customer.getEmail())) {
            customer.setEmail(customer.getEmail().toLowerCase());
        }
        customer.setTaxCode(normalizeText(customer.getTaxCode()));
        customer.setNotes(normalizeText(customer.getNotes()));
    }

    private static String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã khách hàng không được để trống");
        }
        return value.trim().toUpperCase();
    }

    private static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
