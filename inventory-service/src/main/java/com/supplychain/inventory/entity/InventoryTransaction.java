package com.supplychain.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable ledger record of every inventory movement.
 *
 * <p>Once saved, transactions are never deleted or modified.
 * They form the audit trail for traceability queries.
 *
 * <p>Production-grade features:
 * <ul>
 *   <li>Idempotency key — prevents duplicate processing</li>
 *   <li>Supplier traceability — links receipt to supplier</li>
 *   <li>Reference number — links to PO, work order, etc.</li>
 * </ul>
 */
@Entity
@Table(
    name = "inventory_transactions",
    indexes = {
        @Index(name = "idx_trx_no",         columnList = "trx_no",          unique = true),
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_material_id",     columnList = "material_id"),
        @Index(name = "idx_trx_time",        columnList = "trx_time"),
        @Index(name = "idx_trx_type",        columnList = "trx_type")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trx_no", nullable = false, unique = true, length = 50)
    private String trxNo;

    /** Idempotency key — client provides UUID to prevent duplicate transactions */
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    /** Source warehouse (null for initial receipt from supplier) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_warehouse_id")
    private Warehouse sourceWarehouse;

    /** Destination warehouse (null for consumption/issue) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_warehouse_id")
    private Warehouse destinationWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "trx_type", nullable = false, length = 20)
    private TransactionType trxType;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    /** Reference to external documents (PO number, Work Order, etc.) */
    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    /** Supplier code — populated for RECEIPT transactions */
    @Column(name = "supplier_code", length = 50)
    private String supplierCode;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "trx_time", nullable = false)
    private LocalDateTime trxTime;

    public enum TransactionType {
        RECEIPT,     // Received from supplier → destination warehouse
        TRANSFER,    // Moved between warehouses
        RESERVE,     // Reserved for production order
        ISSUE,       // Consumed by production / work order
        ADJUSTMENT,  // Manual stock correction (shrinkage, damage, count)
        RETURN       // Returned from production back to warehouse
    }
}
