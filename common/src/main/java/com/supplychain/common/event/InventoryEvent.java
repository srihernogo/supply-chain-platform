package com.supplychain.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kafka event published by Inventory Service and consumed by Blockchain Ledger Service.
 *
 * <p>Event types:
 * <ul>
 *   <li>INVENTORY_RECEIVED  — material received from supplier</li>
 *   <li>INVENTORY_TRANSFERRED — material moved between warehouses</li>
 *   <li>INVENTORY_ISSUED    — material sent to production</li>
 *   <li>INVENTORY_ADJUSTED  — manual stock adjustment</li>
 *   <li>INVENTORY_RESERVED  — stock reserved for production order</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryEvent {

    /** Kafka topic name */
    public static final String TOPIC = "inventory-events";

    /** DLQ topic for failed processing */
    public static final String DLQ_TOPIC = "inventory-events.DLT";

    // ─── Core Fields ─────────────────────────────────

    private String eventId;         // UUID — for deduplication
    private String eventType;       // INVENTORY_RECEIVED, INVENTORY_TRANSFERRED, etc.
    private String transactionNo;   // e.g. "TRX-2024-001234"
    private String tenantId;        // e.g. "toyota"

    // ─── Business Payload ─────────────────────────────

    private Long       materialId;
    private String     materialCode;
    private String     materialName;

    private Long       sourceWarehouseId;
    private String     sourceWarehouseCode;

    private Long       destinationWarehouseId;
    private String     destinationWarehouseCode;

    private BigDecimal quantity;
    private String     unit;

    private String     supplierId;
    private String     performedBy;    // username

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    // ─── Event Types Constants ────────────────────────

    public static final class EventType {
        public static final String INVENTORY_RECEIVED    = "INVENTORY_RECEIVED";
        public static final String INVENTORY_TRANSFERRED = "INVENTORY_TRANSFERRED";
        public static final String INVENTORY_ISSUED      = "INVENTORY_ISSUED";
        public static final String INVENTORY_ADJUSTED    = "INVENTORY_ADJUSTED";
        public static final String INVENTORY_RESERVED    = "INVENTORY_RESERVED";
    }
}
