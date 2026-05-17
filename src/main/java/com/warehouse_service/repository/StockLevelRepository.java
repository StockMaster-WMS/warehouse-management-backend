package com.warehouse_service.repository;

import com.warehouse_service.entity.StockLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLevelRepository extends JpaRepository<StockLevel, UUID>, JpaSpecificationExecutor<StockLevel> {

	interface StockQuantityView {
		UUID getId();

		UUID getProductId();

		Integer getQtyOnHand();

		Integer getQtyReserved();
	}

	@Override
	@EntityGraph(attributePaths = {"warehouse", "location"})
	Page<StockLevel> findAll(Specification<StockLevel> spec, Pageable pageable);

	List<StockLevel> findByWarehouseId(UUID warehouseId);

	List<StockLevel> findByLocationId(UUID locationId);

	List<StockLevel> findByProductId(UUID productId);

	boolean existsByProductId(UUID productId);

	List<StockLevel> findByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

	boolean existsByWarehouseId(UUID warehouseId);

	boolean existsByLocationId(UUID locationId);

	Optional<StockLevel> findByLocationIdAndProductIdAndLotNumber(UUID locationId, UUID productId, String lotNumber);

	@Query("select count(distinct s.warehouse.id) from StockLevel s where s.qtyOnHand > 0")
	long countWarehousesWithStock();

	@Query("select count(distinct s.productId) from StockLevel s where s.qtyOnHand > 0")
	long countDistinctProducts();

	@Query("select coalesce(sum(s.qtyOnHand), 0) from StockLevel s")
	long sumTotalQtyOnHand();

	@Query("select coalesce(sum(s.qtyReserved), 0) from StockLevel s")
	long sumTotalQtyReserved();

	@Query("select count(s) from StockLevel s where s.expiryDate is not null and s.expiryDate <= :threshold and s.qtyOnHand > 0")
	long countNearExpiry(@Param("threshold") LocalDate threshold);

	@EntityGraph(attributePaths = {"warehouse", "location"})
	@Query("""
			select s from StockLevel s
			where s.expiryDate is not null
			  and s.expiryDate <= :threshold
			  and s.qtyOnHand > 0
			  and (:warehouseId is null or s.warehouse.id = :warehouseId)
			  and (:locationId is null or s.location.id = :locationId)
			  and (:productId is null or s.productId = :productId)
			order by s.expiryDate asc
			""")
	List<StockLevel> findNearExpiry(
			@Param("threshold") LocalDate threshold,
			@Param("warehouseId") UUID warehouseId,
			@Param("locationId") UUID locationId,
			@Param("productId") UUID productId);

	@Query("""
			select s.id as id,
			       s.productId as productId,
			       s.qtyOnHand as qtyOnHand,
			       s.qtyReserved as qtyReserved
			from StockLevel s
			""")
	List<StockQuantityView> findQuantityViews();

	@EntityGraph(attributePaths = {"warehouse", "location"})
	@Query("select s from StockLevel s where s.id in :ids")
	List<StockLevel> findByIdInWithWarehouseAndLocation(@Param("ids") Collection<UUID> ids);

	/**
	 * Find all stock levels in a specific zone within a warehouse
	 */
	@EntityGraph(attributePaths = {"warehouse", "location"})
	@Query("""
			select s from StockLevel s
			where s.warehouse.id = :warehouseId
			  and s.location.zone = :zone
			order by s.location.code asc
			""")
	List<StockLevel> findByWarehouseIdAndZone(@Param("warehouseId") UUID warehouseId, @Param("zone") String zone);

	/**
	 * Find all stock levels in a specific location
	 */
	@EntityGraph(attributePaths = {"warehouse", "location"})
	@Query("select s from StockLevel s where s.location.id = :locationId order by s.productId asc, s.lotNumber asc")
	List<StockLevel> findByLocationIdWithDetails(@Param("locationId") UUID locationId);

	/**
	 * Find all stock levels for a specific product in a warehouse
	 */
	@EntityGraph(attributePaths = {"warehouse", "location"})
	@Query("""
			select s from StockLevel s
			where s.warehouse.id = :warehouseId
			  and s.productId = :productId
			order by s.location.code asc, s.lotNumber asc
			""")
	List<StockLevel> findByWarehouseIdAndProductIdWithDetails(@Param("warehouseId") UUID warehouseId, @Param("productId") UUID productId);
}
