package com.supplychain.inventory.repository;

import com.supplychain.inventory.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByPerformedAtDesc(
            String entityType, String entityId, Pageable pageable);
    Page<AuditLog> findByPerformedByOrderByPerformedAtDesc(String performedBy, Pageable pageable);
    Page<AuditLog> findByTenantIdOrderByPerformedAtDesc(String tenantId, Pageable pageable);
}
