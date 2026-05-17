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
}
