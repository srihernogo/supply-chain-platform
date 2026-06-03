package com.supplychain.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Physical warehouse or storage location where materials are stored.
 * e.g. "WH-MAIN", "WH-RAW-01", "WH-PROD-A"
 */
@Entity
@Table(name = "warehouses", indexes = {
        @Index(name = "idx_warehouse_code", columnList = "warehouse_code", unique = true)
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_code", nullable = false, unique = true, length = 50)
    private String warehouseCode;

    @Column(name = "warehouse_name", nullable = false, length = 200)
    private String warehouseName;

    @Column(name = "location", length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "warehouse_type", nullable = false, length = 30)
    @Builder.Default
    private WarehouseType warehouseType = WarehouseType.GENERAL;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public enum WarehouseType {
        GENERAL,        // General purpose storage
        RAW_MATERIAL,   // Raw materials from suppliers
        WORK_IN_PROGRESS, // WIP / staging area
        FINISHED_GOODS, // Finished products
        QUARANTINE      // Items under quality hold
    }
}
