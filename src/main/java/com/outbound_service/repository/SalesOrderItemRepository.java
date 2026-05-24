package com.outbound_service.repository;

import com.outbound_service.entity.SalesOrderItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderItemRepository
        extends JpaRepository<SalesOrderItem, UUID>, JpaSpecificationExecutor<SalesOrderItem> {

    @Override
    @EntityGraph(attributePaths = { "salesOrder" })
    Page<SalesOrderItem> findAll(Specification<SalesOrderItem> spec, Pageable pageable);

    List<SalesOrderItem> findBySalesOrder_Id(UUID salesOrderId);

    boolean existsBySalesOrder_Id(UUID salesOrderId);

    Optional<SalesOrderItem> findBySalesOrder_IdAndLineNumber(UUID salesOrderId, Short lineNumber);

    @Query("SELECT s FROM SalesOrderItem s JOIN FETCH s.salesOrder WHERE s.id = :id")
    Optional<SalesOrderItem> findByIdWithSalesOrder(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SalesOrderItem s JOIN FETCH s.salesOrder WHERE s.id = :id")
    Optional<SalesOrderItem> findByIdWithSalesOrderForUpdate(@Param("id") UUID id);

    interface TopSkuView {
        UUID getProductId();

        String getProductSku();

        Long getTotalQty();

        java.math.BigDecimal getTotalRevenue();
    }

    @Query(value = """
            select product_id as "productId",
                   product_sku as "productSku",
                   sum(shipped_qty) as "totalQty",
                   sum(unit_price * shipped_qty) as "totalRevenue"
            from sales_order_items
            group by product_id, product_sku
            order by sum(shipped_qty) desc
            limit :limit
            """, nativeQuery = true)
    List<TopSkuView> findTopSkus(@Param("limit") int limit);

    @Query(value = """
            select i.product_id as "productId",
                   i.product_sku as "productSku",
                   sum(i.shipped_qty) as "totalQty",
                   sum(i.unit_price * i.shipped_qty) as "totalRevenue"
            from sales_order_items i
            join sales_orders o on i.sales_order_id = o.id
            where o.status = 'SHIPPED'
              and o.created_at >= :fromDate
              and o.created_at < :toDate
            group by i.product_id, i.product_sku
            order by sum(i.shipped_qty) desc
            limit :limit
            """, nativeQuery = true)
    List<TopSkuView> findTopSkusBetween(
            @Param("fromDate") java.time.OffsetDateTime fromDate,
            @Param("toDate") java.time.OffsetDateTime toDate,
            @Param("limit") int limit);

    @Query(value = """
            select i.product_id as "productId",
                   i.product_sku as "productSku",
                   sum(i.shipped_qty) as "totalQty",
                   sum(i.unit_price * i.shipped_qty) as "totalRevenue"
            from sales_order_items i
            join sales_orders o on i.sales_order_id = o.id
            where o.status = 'SHIPPED'
              and o.created_at >= :fromDate
              and o.created_at < :toDate
              and o.warehouse_id in (:warehouseIds)
            group by i.product_id, i.product_sku
            order by sum(i.shipped_qty) desc
            limit :limit
            """, nativeQuery = true)
    List<TopSkuView> findTopSkusBetweenInWarehouses(
            @Param("fromDate") java.time.OffsetDateTime fromDate,
            @Param("toDate") java.time.OffsetDateTime toDate,
            @Param("warehouseIds") java.util.Collection<UUID> warehouseIds,
            @Param("limit") int limit);

    interface DailyRevenueView {
        java.time.LocalDate getOrderDate();

        java.math.BigDecimal getRevenue();
    }

    @Query(value = """
            select cast(o.created_at as date) as "orderDate",
                   coalesce(sum(i.unit_price * i.shipped_qty), 0) as "revenue"
            from sales_order_items i
            join sales_orders o on i.sales_order_id = o.id
            where o.status = 'SHIPPED'
              and o.created_at >= :fromDate
              and o.created_at < :toDate
            group by cast(o.created_at as date)
            order by cast(o.created_at as date)
            """, nativeQuery = true)
    List<DailyRevenueView> sumDailyRevenue(
            @Param("fromDate") java.time.OffsetDateTime fromDate,
            @Param("toDate") java.time.OffsetDateTime toDate);

    @Query(value = """
            select cast(o.created_at as date) as "orderDate",
                   coalesce(sum(i.unit_price * i.shipped_qty), 0) as "revenue"
            from sales_order_items i
            join sales_orders o on i.sales_order_id = o.id
            where o.status = 'SHIPPED'
              and o.created_at >= :fromDate
              and o.created_at < :toDate
              and o.warehouse_id in (:warehouseIds)
            group by cast(o.created_at as date)
            order by cast(o.created_at as date)
            """, nativeQuery = true)
    List<DailyRevenueView> sumDailyRevenueInWarehouses(
            @Param("fromDate") java.time.OffsetDateTime fromDate,
            @Param("toDate") java.time.OffsetDateTime toDate,
            @Param("warehouseIds") java.util.Collection<UUID> warehouseIds);

    interface ShippedItemReportView {
        UUID getOrderId();

        String getSoNumber();

        OffsetDateTime getCreatedAt();

        UUID getWarehouseId();

        String getCustomerName();

        UUID getProductId();

        String getProductSku();

        Integer getOrderedQty();

        Integer getShippedQty();

        java.math.BigDecimal getUnitPrice();

        java.math.BigDecimal getRevenue();
    }

    @Query(value = """
            select o.id as "orderId",
                   o.so_number as "soNumber",
                   o.created_at as "createdAt",
                   o.warehouse_id as "warehouseId",
                   o.customer_name as "customerName",
                   i.product_id as "productId",
                   i.product_sku as "productSku",
                   i.ordered_qty as "orderedQty",
                   i.shipped_qty as "shippedQty",
                   i.unit_price as "unitPrice",
                   coalesce(i.unit_price * i.shipped_qty, 0) as "revenue"
            from sales_order_items i
            join sales_orders o on i.sales_order_id = o.id
            where o.status = 'SHIPPED'
              and o.created_at >= :fromDate
              and o.created_at < :toDate
            order by o.created_at desc, o.so_number asc, i.line_number asc
            """, nativeQuery = true)
    List<ShippedItemReportView> findShippedItemDetailsBetween(
            @Param("fromDate") java.time.OffsetDateTime fromDate,
            @Param("toDate") java.time.OffsetDateTime toDate);

    @Query(value = """
            select o.id as "orderId",
                   o.so_number as "soNumber",
                   o.created_at as "createdAt",
                   o.warehouse_id as "warehouseId",
                   o.customer_name as "customerName",
                   i.product_id as "productId",
                   i.product_sku as "productSku",
                   i.ordered_qty as "orderedQty",
                   i.shipped_qty as "shippedQty",
                   i.unit_price as "unitPrice",
                   coalesce(i.unit_price * i.shipped_qty, 0) as "revenue"
            from sales_order_items i
            join sales_orders o on i.sales_order_id = o.id
            where o.status = 'SHIPPED'
              and o.created_at >= :fromDate
              and o.created_at < :toDate
              and o.warehouse_id in (:warehouseIds)
            order by o.created_at desc, o.so_number asc, i.line_number asc
            """, nativeQuery = true)
    List<ShippedItemReportView> findShippedItemDetailsBetweenInWarehouses(
            @Param("fromDate") java.time.OffsetDateTime fromDate,
            @Param("toDate") java.time.OffsetDateTime toDate,
            @Param("warehouseIds") java.util.Collection<UUID> warehouseIds);
}
