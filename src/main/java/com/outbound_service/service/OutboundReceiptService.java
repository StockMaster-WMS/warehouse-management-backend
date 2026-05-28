package com.outbound_service.service;

import com.common.exception.AppException;
import com.common.exception.ErrorCode;
import com.inbound_service.entity.Rma;
import com.inbound_service.entity.RmaItem;
import com.inbound_service.repository.RmaRepository;
import com.outbound_service.dto.response.OutboundReceiptDetailResponse;
import com.outbound_service.dto.response.OutboundReceiptSummaryResponse;
import com.outbound_service.entity.Customer;
import com.outbound_service.entity.PickingItem;
import com.outbound_service.entity.SalesOrder;
import com.outbound_service.entity.SalesOrderItem;
import com.outbound_service.entity.SalesOrderStatus;
import com.outbound_service.repository.CustomerRepository;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.SalesOrderRepository;
import com.product_service.dto.response.ProductSummaryResponse;
import com.product_service.service.ProductService;
import com.warehouse_service.entity.Location;
import com.warehouse_service.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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
    private final PickingItemRepository pickingItemRepository;
    private final LocationRepository locationRepository;
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
        if (order.getStatus() != SalesOrderStatus.SHIPPED && order.getStatus() != SalesOrderStatus.COMPLETED) {
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
        List<PickingItem> picks = pickingItemRepository.findBySalesOrderIdWithSoItem(order.getId());
        if (!picks.isEmpty()) {
            Map<ReturnSourceKey, Integer> returnedBySource = returnedQtyBySource(order.getId());
            Map<UUID, String> locationCodeById = locationRepository.findAllById(picks.stream()
                            .map(PickingItem::getLocationId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList())
                    .stream()
                    .collect(Collectors.toMap(Location::getId, Location::getCode));
            Map<ReturnSourceKey, PickReceiptLine> groupedPicks = new LinkedHashMap<>();
            for (PickingItem pick : picks) {
                if (pick.getSoItem() == null) {
                    continue;
                }
                ReturnSourceKey key = new ReturnSourceKey(
                        pick.getSoItem().getId(),
                        pick.getLocationId(),
                        pick.getProductId(),
                        normalizeLot(pick.getLotNumber()));
                PickReceiptLine line = groupedPicks.computeIfAbsent(key, ignored -> new PickReceiptLine(pick));
                line.shippedQty += safeQty(pick.getQtyPicked());
            }
            List<OutboundReceiptDetailResponse.ItemResponse> pickedItems = groupedPicks.values().stream()
                    .map(line -> {
                        PickingItem pick = line.pick;
                        ProductSummaryResponse product = products.get(pick.getProductId());
                        ReturnSourceKey key = new ReturnSourceKey(
                                pick.getSoItem().getId(),
                                pick.getLocationId(),
                                pick.getProductId(),
                                normalizeLot(pick.getLotNumber()));
                        int returned = returnedBySource.getOrDefault(key, 0);
                        int shipped = line.shippedQty;
                        return new OutboundReceiptDetailResponse.ItemResponse(
                                pick.getSoItem().getId(),
                                pick.getProductId(),
                                pick.getSoItem().getProductSku(),
                                product == null ? null : product.name(),
                                pick.getLocationId(),
                                locationCodeById.get(pick.getLocationId()),
                                normalizeLot(pick.getLotNumber()),
                                shipped,
                                returned,
                                Math.max(shipped - returned, 0));
                    })
                    .toList();
            return buildDetailResponse(order, pickedItems);
        }

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
                            null,
                            null,
                            null,
                            shipped,
                            returned,
                            Math.max(shipped - returned, 0));
                })
                .toList();
        return buildDetailResponse(order, items);
    }

    private OutboundReceiptDetailResponse buildDetailResponse(
            SalesOrder order,
            List<OutboundReceiptDetailResponse.ItemResponse> items) {
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

    private Map<ReturnSourceKey, Integer> returnedQtyBySource(UUID salesOrderId) {
        return rmaRepository.findBySalesOrderIdWithItems(salesOrderId).stream()
                .filter(rma -> "CUSTOMER".equalsIgnoreCase(rma.getReturnType()))
                .filter(rma -> rma.getStatus() != Rma.RmaStatus.REJECTED && rma.getStatus() != Rma.RmaStatus.CANCELLED)
                .flatMap(rma -> rma.getItems().stream())
                .filter(item -> item.getSalesOrderItemId() != null && item.getReturnLocationId() != null)
                .collect(Collectors.groupingBy(
                        item -> new ReturnSourceKey(
                                item.getSalesOrderItemId(),
                                item.getReturnLocationId(),
                                item.getProductId(),
                                normalizeLot(item.getLotNumber())),
                        LinkedHashMap::new,
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

    private String normalizeLot(String lotNumber) {
        return lotNumber == null ? "" : lotNumber.trim();
    }

    private record ReturnSourceKey(UUID salesOrderItemId, UUID locationId, UUID productId, String lotNumber) {
    }

    private static class PickReceiptLine {
        private final PickingItem pick;
        private int shippedQty;

        private PickReceiptLine(PickingItem pick) {
            this.pick = pick;
        }
    }
}
