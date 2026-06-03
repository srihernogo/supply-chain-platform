package com.supplychain.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Current stock balance for a (Material × Warehouse) combination.
 *
 * <p>Uses optimistic locking via {@code @Version} to safely handle
 * concurrent inventory updates (e.g. two receipts for the same material).
 *
 * <p>The version field is incremented on every update. If two transactions
 * read the same version and both try to save, the second will throw
 * {@link jakarta.persistence.OptimisticLockException}.
 */
@Entity
@Table(
    name = "inventory",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_inventory_material_warehouse",
        columnNames = {"material_id", "warehouse_id"}
    )
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "reserved_quantity", nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    /**
     * Optimistic locking version — automatically managed by JPA.
     * Prevents lost updates in concurrent scenarios.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Computed: available = quantity - reservedQuantity */
    @Transient
    public BigDecimal getAvailableQuantity() {
        return quantity.subtract(reservedQuantity);
    }
}
