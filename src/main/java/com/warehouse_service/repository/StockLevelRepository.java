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

		UUID getWarehouseId();

		Integer getQtyOnHand();

		Integer getQtyReserved();
	}

	interface CycleCountStockSnapshotView {
		UUID getProductId();

		UUID getLocationId();

		String getLotNumber();

		Integer getQtyOnHand();
	}

	@Override
	@EntityGraph(attributePaths = {"warehouse", "location"})
	Page<StockLevel> findAll(Specification<StockLevel> spec, Pageable pageable);

	List<StockLevel> findByWarehouseId(UUID warehouseId);

	@EntityGraph(attributePaths = {"warehouse", "location"})
	@Query("""
			select s from StockLevel s
			where s.warehouse.id = :warehouseId
			order by s.location.code asc, s.productId asc, s.lotNumber asc
			""")
	List<StockLevel> findByWarehouseIdWithDetails(@Param("warehouseId") UUID warehouseId);

	@Query("""
			select s.productId as productId,
			       s.location.id as locationId,
			       s.lotNumber as lotNumber,
			       s.qtyOnHand as qtyOnHand
			from StockLevel s
			where s.warehouse.id = :warehouseId
			order by s.location.code asc, s.productId asc, s.lotNumber asc
			""")
	List<CycleCountStockSnapshotView> findCycleCountSnapshotsByWarehouseId(@Param("warehouseId") UUID warehouseId);

	List<StockLevel> findByLocationId(UUID locationId);

	List<StockLevel> findByProductId(UUID productId);

	boolean existsByProductId(UUID productId);

	List<StockLevel> findByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

	boolean existsByWarehouseId(UUID warehouseId);

	boolean existsByLocationId(UUID locationId);

	Optional<StockLevel> findByLocationIdAndProductIdAndLotNumber(UUID locationId, UUID productId, String lotNumber);

	@Query("select count(distinct s.warehouse.id) from StockLevel s where s.qtyOnHand > 0")
	long countWarehousesWithStock();

	@Query("select count(distinct s.warehouse.id) from StockLevel s where s.qtyOnHand > 0 and s.warehouse.id in :warehouseIds")
	long countWarehousesWithStockByWarehouseIds(@Param("warehouseIds") Collection<UUID> warehouseIds);

	@Query("select count(distinct s.productId) from StockLevel s where s.qtyOnHand > 0")
	long countDistinctProducts();

	@Query("select count(distinct s.productId) from StockLevel s where s.qtyOnHand > 0 and s.warehouse.id in :warehouseIds")
	long countDistinctProductsByWarehouseIds(@Param("warehouseIds") Collection<UUID> warehouseIds);

	@Query("select coalesce(sum(s.qtyOnHand), 0) from StockLevel s")
	long sumTotalQtyOnHand();

	@Query("select coalesce(sum(s.qtyOnHand), 0) from StockLevel s where s.warehouse.id in :warehouseIds")
	long sumTotalQtyOnHandByWarehouseIds(@Param("warehouseIds") Collection<UUID> warehouseIds);

	@Query("select coalesce(sum(s.qtyReserved), 0) from StockLevel s")
	long sumTotalQtyReserved();

	@Query("select coalesce(sum(s.qtyReserved), 0) from StockLevel s where s.warehouse.id in :warehouseIds")
	long sumTotalQtyReservedByWarehouseIds(@Param("warehouseIds") Collection<UUID> warehouseIds);

	@Query("select count(s) from StockLevel s where s.expiryDate is not null and s.expiryDate <= :threshold and s.qtyOnHand > 0")
	long countNearExpiry(@Param("threshold") LocalDate threshold);

	@Query("select count(s) from StockLevel s where s.expiryDate is not null and s.expiryDate <= :threshold and s.qtyOnHand > 0 and s.warehouse.id in :warehouseIds")
	long countNearExpiryByWarehouseIds(@Param("threshold") LocalDate threshold, @Param("warehouseIds") Collection<UUID> warehouseIds);

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
			       s.warehouse.id as warehouseId,
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
			  and lower(s.location.zone) = lower(:zone)
			order by s.location.code asc
			""")
	List<StockLevel> findByWarehouseIdAndZone(@Param("warehouseId") UUID warehouseId, @Param("zone") String zone);

	@Query("""
			select s.productId as productId,
			       s.location.id as locationId,
			       s.lotNumber as lotNumber,
			       s.qtyOnHand as qtyOnHand
			from StockLevel s
			where s.warehouse.id = :warehouseId
			  and lower(s.location.zone) = lower(:zone)
			order by s.location.code asc, s.productId asc, s.lotNumber asc
			""")
	List<CycleCountStockSnapshotView> findCycleCountSnapshotsByWarehouseIdAndZone(
			@Param("warehouseId") UUID warehouseId,
			@Param("zone") String zone);

	/**
	 * Find all stock levels in a specific location
	 */
	@EntityGraph(attributePaths = {"warehouse", "location"})
	@Query("select s from StockLevel s where s.location.id = :locationId order by s.productId asc, s.lotNumber asc")
	List<StockLevel> findByLocationIdWithDetails(@Param("locationId") UUID locationId);

	@EntityGraph(attributePaths = {"warehouse", "location"})
	@Query("""
			select s from StockLevel s
			where s.warehouse.id = :warehouseId
			  and s.location.id = :locationId
			order by s.productId asc, s.lotNumber asc
			""")
	List<StockLevel> findByWarehouseIdAndLocationIdWithDetails(
			@Param("warehouseId") UUID warehouseId,
			@Param("locationId") UUID locationId);

	@Query("""
			select s.productId as productId,
			       s.location.id as locationId,
			       s.lotNumber as lotNumber,
			       s.qtyOnHand as qtyOnHand
			from StockLevel s
			where s.warehouse.id = :warehouseId
			  and s.location.id = :locationId
			order by s.productId asc, s.lotNumber asc
			""")
	List<CycleCountStockSnapshotView> findCycleCountSnapshotsByWarehouseIdAndLocationId(
			@Param("warehouseId") UUID warehouseId,
			@Param("locationId") UUID locationId);

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

	@Query("""
			select s.productId as productId,
			       s.location.id as locationId,
			       s.lotNumber as lotNumber,
			       s.qtyOnHand as qtyOnHand
			from StockLevel s
			where s.warehouse.id = :warehouseId
			  and s.productId = :productId
			order by s.location.code asc, s.lotNumber asc
			""")
	List<CycleCountStockSnapshotView> findCycleCountSnapshotsByWarehouseIdAndProductId(
			@Param("warehouseId") UUID warehouseId,
			@Param("productId") UUID productId);

	@Query("""
			select s.productId as productId,
			       s.location.id as locationId,
			       s.lotNumber as lotNumber,
			       s.qtyOnHand as qtyOnHand
			from StockLevel s
			where s.warehouse.id = :warehouseId
			  and s.location.id = :locationId
			  and s.productId = :productId
			  and s.lotNumber = :lotNumber
			""")
	Optional<CycleCountStockSnapshotView> findCycleCountSnapshot(
			@Param("warehouseId") UUID warehouseId,
			@Param("locationId") UUID locationId,
			@Param("productId") UUID productId,
			@Param("lotNumber") String lotNumber);
}
