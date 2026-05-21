package com.outbound_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.entity.Rma;
import com.inbound_service.entity.RmaItem;
import com.inbound_service.repository.RmaRepository;
import com.outbound_service.dto.response.OutboundReceiptDetailResponse;
import com.outbound_service.dto.response.OutboundReceiptSummaryResponse;
import com.outbound_service.entity.Customer;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.repository.CustomerRepository;
import com.outbound_service.repository.SalesOrderRepository;
import com.product_service.dto.response.ProductSummaryResponse;
import com.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OutboundReceiptService {

    private final CustomerRepository customerRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final RmaRepository rmaRepository;
    private final ProductService productService;

    public List<OutboundReceiptSummaryResponse> findByCustomer(UUID customerId, Collection<UUID> visibleWarehouseIds) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khách hàng"));
        List<SalesOrder> orders = visibleWarehouseIds == null
                ? salesOrderRepository.findReturnableByCustomer(customerId, customer.getName())
                : salesOrderRepository.findReturnableByCustomerInWarehouses(customerId, customer.getName(), visibleWarehouseIds);
        return orders.stream()
                .map(order -> toSummary(order, returnedQtyByProduct(order.getId())))
                .toList();
    }

    public OutboundReceiptDetailResponse getDetails(UUID salesOrderId, Collection<UUID> visibleWarehouseIds) {
        SalesOrder order = salesOrderRepository.findWithItemsById(salesOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn xuất"));
        assertVisible(order, visibleWarehouseIds);
        if (order.getStatus() != SalesOrderStatus.SHIPPED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ tạo trả hàng từ đơn xuất đã giao");
        }
        return toDetail(order, returnedQtyByProduct(order.getId()));
    }

    private OutboundReceiptSummaryResponse toSummary(SalesOrder order, Map<UUID, Integer> returnedByProduct) {
        int totalShipped = order.getItems() == null ? 0 : order.getItems().stream()
                .mapToInt(item -> safeQty(item.getShippedQty()))
                .sum();
        int totalReturnable = order.getItems() == null ? 0 : order.getItems().stream()
                .mapToInt(item -> Math.max(safeQty(item.getShippedQty()) - returnedByProduct.getOrDefault(item.getProductId(), 0), 0))
                .sum();
        return new OutboundReceiptSummaryResponse(
                order.getId(),
                order.getSoNumber(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getWarehouseId(),
                order.getStatus() == null ? null : order.getStatus().name(),
                order.getCreatedAt(),
                totalShipped,
                totalReturnable);
    }

    private OutboundReceiptDetailResponse toDetail(SalesOrder order, Map<UUID, Integer> returnedByProduct) {
        Map<UUID, ProductSummaryResponse> products = loadProducts(order);
        List<OutboundReceiptDetailResponse.ItemResponse> items = order.getItems() == null ? List.of() : order.getItems().stream()
                .map(item -> {
                    int shipped = safeQty(item.getShippedQty());
                    int returned = returnedByProduct.getOrDefault(item.getProductId(), 0);
                    ProductSummaryResponse product = products.get(item.getProductId());
                    return new OutboundReceiptDetailResponse.ItemResponse(
                            item.getId(),
                            item.getProductId(),
                            item.getProductSku(),
                            product == null ? null : product.name(),
                            shipped,
                            returned,
                            Math.max(shipped - returned, 0));
                })
                .toList();
        return new OutboundReceiptDetailResponse(
                order.getId(),
                order.getSoNumber(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getWarehouseId(),
                order.getStatus() == null ? null : order.getStatus().name(),
                order.getCreatedAt(),
                items);
    }

    private Map<UUID, Integer> returnedQtyByProduct(UUID salesOrderId) {
        return rmaRepository.findBySalesOrderIdWithItems(salesOrderId).stream()
                .filter(rma -> "CUSTOMER".equalsIgnoreCase(rma.getReturnType()))
                .filter(rma -> rma.getStatus() != Rma.RmaStatus.REJECTED && rma.getStatus() != Rma.RmaStatus.CANCELLED)
                .flatMap(rma -> rma.getItems().stream())
                .collect(Collectors.groupingBy(
                        RmaItem::getProductId,
                        Collectors.summingInt(item -> safeQty(item.getExpectedQty()))));
    }

    private Map<UUID, ProductSummaryResponse> loadProducts(SalesOrder order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = order.getItems().stream()
                .map(SalesOrderItem::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return productService.findSummariesByIds(new ArrayList<>(ids)).stream()
                    .collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity()));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private void assertVisible(SalesOrder order, Collection<UUID> visibleWarehouseIds) {
        if (visibleWarehouseIds != null
                && (visibleWarehouseIds.isEmpty() || !visibleWarehouseIds.contains(order.getWarehouseId()))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được xem đơn xuất của kho này");
        }
    }

    private int safeQty(Integer qty) {
        return qty == null ? 0 : qty;
    }
}
