package com.supplychain.inventory.repository;

import com.supplychain.inventory.entity.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /** Idempotency check — return existing transaction if already processed */
    Optional<InventoryTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<InventoryTransaction> findByTrxNo(String trxNo);

    /** Full traceability history for a material, ordered by time ascending */
    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.material.id = :materialId
        ORDER BY t.trxTime ASC
    """)
    List<InventoryTransaction> findByMaterialIdOrderByTrxTimeAsc(@Param("materialId") Long materialId);

    /** Transaction history for a warehouse */
    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.sourceWarehouse.id = :warehouseId
           OR t.destinationWarehouse.id = :warehouseId
        ORDER BY t.trxTime DESC
    """)
    Page<InventoryTransaction> findByWarehouseId(@Param("warehouseId") Long warehouseId, Pageable pageable);

    /** Supplier traceability — which transactions came from this supplier */
    List<InventoryTransaction> findBySupplierCodeOrderByTrxTimeAsc(String supplierCode);

    /** Transactions within a time range */
    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.trxTime BETWEEN :from AND :to
        ORDER BY t.trxTime DESC
    """)
    Page<InventoryTransaction> findByTimeRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
