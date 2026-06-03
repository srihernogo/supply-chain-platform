package com.supplychain.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * System-level audit log — records every significant action.
 * Separate from inventory transactions (which are business records).
 * This captures who did what and when, for compliance.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_entity",    columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_performed", columnList = "performed_by"),
        @Index(name = "idx_audit_time",      columnList = "performed_at")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType; // "INVENTORY_TRANSACTION", "MATERIAL", etc.

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action; // "RECEIPT", "TRANSFER", "ISSUE", "CREATE", "UPDATE"

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details; // JSON snapshot of the operation

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    public static AuditLog of(String entityType, String entityId, String action,
                               String performedBy, String tenantId, String details) {
        return AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy(performedBy)
                .tenantId(tenantId)
                .details(details)
                .performedAt(LocalDateTime.now())
                .build();
    }
}
