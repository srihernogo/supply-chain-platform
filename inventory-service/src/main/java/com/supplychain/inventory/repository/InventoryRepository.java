package com.supplychain.inventory.repository;

import com.supplychain.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /** Find stock balance for a specific material+warehouse combination */
    Optional<Inventory> findByMaterialIdAndWarehouseId(Long materialId, Long warehouseId);

    /**
     * Find with OPTIMISTIC lock — ensures version check on save.
     * Use this before any quantity update to detect concurrent modifications.
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT i FROM Inventory i WHERE i.material.id = :materialId AND i.warehouse.id = :warehouseId")
    Optional<Inventory> findByMaterialIdAndWarehouseIdForUpdate(
            @Param("materialId") Long materialId,
            @Param("warehouseId") Long warehouseId);

    /**
     * Find with PESSIMISTIC lock (SELECT ... FOR UPDATE) — acquires exclusive row
     * lock immediately.
     * Use this for write-heavy operations (especially issue/deduction) to prevent
     * race conditions
     * entirely instead of retrying on conflicts. More efficient for
     * high-concurrency scenarios.
     *
     * @param materialId  Material ID
     * @param warehouseId Warehouse ID
     * @return Inventory with exclusive database lock, or empty if not found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.material.id = :materialId AND i.warehouse.id = :warehouseId")
    Optional<Inventory> findByMaterialIdAndWarehouseIdForUpdatePessimistic(
            @Param("materialId") Long materialId,
            @Param("warehouseId") Long warehouseId);

    /** All stock for a given material across all warehouses */
    @Query("SELECT i FROM Inventory i WHERE i.material.id = :materialId")
    List<Inventory> findByMaterialId(@Param("materialId") Long materialId);

    /** All stock in a given warehouse */
    @Query("SELECT i FROM Inventory i WHERE i.warehouse.id = :warehouseId AND i.quantity > 0")
    List<Inventory> findByWarehouseIdWithStock(@Param("warehouseId") Long warehouseId);

    /** Find materials below min stock level — for alerts */
    @Query("""
                SELECT i FROM Inventory i
                JOIN i.material m
                WHERE m.minStockLevel IS NOT NULL
                  AND i.quantity < m.minStockLevel
            """)
    List<Inventory> findBelowMinStockLevel();
}
