package com.supplychain.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Material master data — the item being tracked through the supply chain.
 * e.g. "Steel Coil SPC270", "Bolt M12 x 50mm", "Plastic Cover Type A"
 */
@Entity
@Table(name = "materials", indexes = {
        @Index(name = "idx_material_code", columnList = "material_code", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_code", nullable = false, unique = true, length = 50)
    private String materialCode;

    @Column(name = "material_name", nullable = false, length = 200)
    private String materialName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit; // KG, PCS, MTR, SET, etc.

    @Column(name = "category", length = 100)
    private String category;

    /** Minimum stock level — triggers alert when below this */
    @Column(name = "min_stock_level")
    private Integer minStockLevel;

    @Column(name = "supplier_code", length = 50)
    private String supplierCode; // Reference to supplier

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
