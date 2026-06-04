package com.supplychain.inventory.service;

import com.supplychain.common.exception.ResourceNotFoundException;
import com.supplychain.common.tenant.TenantContext;
import com.supplychain.inventory.dto.*;
import com.supplychain.inventory.entity.*;
import com.supplychain.inventory.entity.InventoryTransaction.TransactionType;
import com.supplychain.inventory.outbox.OutboxService;
import com.supplychain.inventory.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core inventory business logic service with comprehensive race condition
 * prevention.
 *
 * <p>
 * <b>Production-grade features implemented:</b>
 * <ul>
 * <li>Idempotency — duplicate requests safely ignored via unique idempotency
 * key</li>
 * <li><b>Pessimistic Locking (SELECT ... FOR UPDATE)</b> — Critical for
 * deduction operations (ISSUE, TRANSFER)
 * to acquire exclusive row lock immediately, preventing race conditions
 * entirely</li>
 * <li>Optimistic Locking with @Version — For lower-contention scenarios;
 * includes automatic retry</li>
 * <li>Audit Log — every action recorded with user, time, and details</li>
 * <li>Transactional Outbox — Kafka events persisted in the same DB transaction,
 * relayed asynchronously</li>
 * <li>Metrics — Prometheus counters for every transaction type</li>
 * </ul>
 *
 * <p>
 * <b>Race Condition Prevention Strategy:</b>
 * <ul>
 * <li><b>ISSUE</b> (deduction from warehouse): Uses
 * {@code findByMaterialIdAndWarehouseIdForUpdatePessimistic}
 * to lock the inventory row with SELECT ... FOR UPDATE, preventing concurrent
 * reads of stale quantity.</li>
 * <li><b>TRANSFER</b> (deduction + addition): Source warehouse uses pessimistic
 * lock; destination uses optimistic.</li>
 * <li><b>RECEIPT</b> (addition to warehouse): Uses optimistic lock with retry
 * for better throughput.</li>
 * <li><b>ADJUSTMENT</b> (manual correction): Uses optimistic lock with
 * retry.</li>
 * </ul>
 */
@Slf4j
@Service
public class InventoryService {

        private static final DateTimeFormatter TRX_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

        private final InventoryTransactionRepository trxRepo;
        private final InventoryRepository inventoryRepo;
        private final MaterialRepository materialRepo;
        private final WarehouseRepository warehouseRepo;
        private final AuditLogRepository auditLogRepo;
        private final OutboxService outboxService;

        // Prometheus counters
        private final Counter receiptCounter;
        private final Counter transferCounter;
        private final Counter issueCounter;
        private final Counter adjustmentCounter;

        private final AtomicLong trxSequence = new AtomicLong(0);

        public InventoryService(InventoryTransactionRepository trxRepo,
                        InventoryRepository inventoryRepo,
                        MaterialRepository materialRepo,
                        WarehouseRepository warehouseRepo,
                        AuditLogRepository auditLogRepo,
                        OutboxService outboxService,
                        MeterRegistry meterRegistry) {
                this.trxRepo = trxRepo;
                this.inventoryRepo = inventoryRepo;
                this.materialRepo = materialRepo;
                this.warehouseRepo = warehouseRepo;
                this.auditLogRepo = auditLogRepo;
                this.outboxService = outboxService;

                this.receiptCounter = Counter.builder("inventory.transaction.receipt").register(meterRegistry);
                this.transferCounter = Counter.builder("inventory.transaction.transfer").register(meterRegistry);
                this.issueCounter = Counter.builder("inventory.transaction.issue").register(meterRegistry);
                this.adjustmentCounter = Counter.builder("inventory.transaction.adjustment").register(meterRegistry);
        }

        // ═══════════════════════════════════════════════════════
        // RECEIPT — Material received from supplier
        // ═══════════════════════════════════════════════════════

        /**
         * Process a material receipt from supplier into warehouse.
         *
         * <p>
         * Flow:
         * <ol>
         * <li>Check idempotency — return if already processed</li>
         * <li>Load & validate material and warehouse</li>
         * <li>Create InventoryTransaction record</li>
         * <li>Update Inventory balance (optimistic locking)</li>
         * <li>Save AuditLog</li>
         * <li>Enqueue outbox event (same transaction) → relay publishes to Kafka for
         * blockchain</li>
         * </ol>
         */
        @Transactional
        @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
        public InventoryTransactionResponse processReceipt(ReceiptRequest req, String idempotencyKey, String username) {
                // 1. Idempotency check
                Optional<InventoryTransaction> existing = trxRepo.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                        log.info("Duplicate receipt request — returning existing: idempotencyKey={}", idempotencyKey);
                        return InventoryTransactionResponse.from(existing.get());
                }

                // 2. Validate material & warehouse
                Material material = getMaterial(req.getMaterialId());
                Warehouse warehouse = getWarehouse(req.getWarehouseId());

                // 3. Create transaction record
                String trxNo = generateTrxNo("RCP");
                InventoryTransaction trx = InventoryTransaction.builder()
                                .trxNo(trxNo)
                                .idempotencyKey(idempotencyKey)
                                .material(material)
                                .destinationWarehouse(warehouse)
                                .trxType(TransactionType.RECEIPT)
                                .quantity(req.getQuantity())
                                .supplierCode(req.getSupplierCode())
                                .referenceNo(req.getPurchaseOrderNo())
                                .notes(req.getNotes())
                                .performedBy(username)
                                .trxTime(LocalDateTime.now())
                                .build();
                trxRepo.save(trx);

                // 4. Update inventory balance (create if first time for this
                // material+warehouse)
                Inventory inventory = inventoryRepo
                                .findByMaterialIdAndWarehouseIdForUpdate(material.getId(), warehouse.getId())
                                .orElse(Inventory.builder().material(material).warehouse(warehouse)
                                                .quantity(BigDecimal.ZERO).reservedQuantity(BigDecimal.ZERO).build());
                inventory.setQuantity(inventory.getQuantity().add(req.getQuantity()));
                inventoryRepo.save(inventory);

                // 5. Audit log
                String tenantId = TenantContext.getCurrentTenant();
                auditLogRepo.save(AuditLog.of(
                                "INVENTORY_TRANSACTION", trxNo, "RECEIPT", username, tenantId,
                                String.format("{\"material\":\"%s\",\"warehouse\":\"%s\",\"qty\":%s,\"supplier\":\"%s\"}",
                                                material.getMaterialCode(), warehouse.getWarehouseCode(),
                                                req.getQuantity(), req.getSupplierCode())));

                // 6. Transactional outbox (same DB transaction as stock update)
                outboxService.enqueueReceiptEvent(trx, material, warehouse, tenantId);

                receiptCounter.increment();
                log.info("Receipt processed: trxNo={}, material={}, warehouse={}, qty={}, tenant={}",
                                trxNo, material.getMaterialCode(), warehouse.getWarehouseCode(), req.getQuantity(),
                                tenantId);

                return InventoryTransactionResponse.from(trx);
        }

        // ═══════════════════════════════════════════════════════
        // TRANSFER — Move material between warehouses
        // ═══════════════════════════════════════════════════════

        /**
         * Process material transfer between warehouses (deduction from source +
         * addition to destination).
         *
         * <p>
         * Lock Strategy:
         * <ul>
         * <li><b>Source warehouse:</b> Uses PESSIMISTIC_WRITE lock to prevent race
         * condition on deduction</li>
         * <li><b>Destination warehouse:</b> Uses optimistic lock (lower contention,
         * addition is safer)</li>
         * </ul>
         *
         * @param req            Transfer request (material, source, destination,
         *                       quantity)
         * @param idempotencyKey Unique key for idempotent processing
         * @param username       User performing the operation
         * @return Transaction response
         * @throws IllegalStateException if source stock insufficient
         */
        @Transactional
        @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
        public InventoryTransactionResponse processTransfer(TransferRequest req, String idempotencyKey,
                        String username) {
                Optional<InventoryTransaction> existing = trxRepo.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent())
                        return InventoryTransactionResponse.from(existing.get());

                Material material = getMaterial(req.getMaterialId());
                Warehouse sourceWh = getWarehouse(req.getSourceWarehouseId());
                Warehouse destWh = getWarehouse(req.getDestinationWarehouseId());

                if (sourceWh.getId().equals(destWh.getId())) {
                        throw new IllegalArgumentException("Source and destination warehouse must be different");
                }

                // Check sufficient stock — use PESSIMISTIC lock on source to prevent race
                // condition on deduction
                Inventory sourceInventory = inventoryRepo
                                .findByMaterialIdAndWarehouseIdForUpdatePessimistic(material.getId(), sourceWh.getId())
                                .orElseThrow(() -> new IllegalStateException(
                                                "No stock for material " + material.getMaterialCode() + " in warehouse "
                                                                + sourceWh.getWarehouseCode()));

                if (sourceInventory.getAvailableQuantity().compareTo(req.getQuantity()) < 0) {
                        throw new IllegalStateException(String.format(
                                        "Insufficient stock: available=%.3f, requested=%.3f",
                                        sourceInventory.getAvailableQuantity(), req.getQuantity()));
                }

                // Deduct from source (pessimistic lock already acquired)
                sourceInventory.setQuantity(sourceInventory.getQuantity().subtract(req.getQuantity()));
                inventoryRepo.save(sourceInventory);

                // Add to destination (optimistic lock with retry on conflict)
                Inventory destInventory = inventoryRepo
                                .findByMaterialIdAndWarehouseIdForUpdate(material.getId(), destWh.getId())
                                .orElse(Inventory.builder().material(material).warehouse(destWh)
                                                .quantity(BigDecimal.ZERO).reservedQuantity(BigDecimal.ZERO).build());
                destInventory.setQuantity(destInventory.getQuantity().add(req.getQuantity()));
                inventoryRepo.save(destInventory);

                // Create transaction
                String trxNo = generateTrxNo("TRF");
                InventoryTransaction trx = InventoryTransaction.builder()
                                .trxNo(trxNo).idempotencyKey(idempotencyKey).material(material)
                                .sourceWarehouse(sourceWh).destinationWarehouse(destWh)
                                .trxType(TransactionType.TRANSFER).quantity(req.getQuantity())
                                .referenceNo(req.getReferenceNo()).notes(req.getNotes())
                                .performedBy(username).trxTime(LocalDateTime.now()).build();
                trxRepo.save(trx);

                String tenantId = TenantContext.getCurrentTenant();
                auditLogRepo.save(AuditLog.of("INVENTORY_TRANSACTION", trxNo, "TRANSFER", username, tenantId,
                                String.format("{\"material\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"qty\":%s}",
                                                material.getMaterialCode(), sourceWh.getWarehouseCode(),
                                                destWh.getWarehouseCode(), req.getQuantity())));

                outboxService.enqueueTransferEvent(trx, material, sourceWh, destWh, tenantId);
                transferCounter.increment();
                log.info("Transfer processed: trxNo={}, material={}, from={}, to={}, qty={}, source_locked=PESSIMISTIC",
                                trxNo, material.getMaterialCode(), sourceWh.getWarehouseCode(),
                                destWh.getWarehouseCode(), req.getQuantity());

                return InventoryTransactionResponse.from(trx);
        }

        // ═══════════════════════════════════════════════════════
        // ISSUE — Consume material for production
        // ═══════════════════════════════════════════════════════

        /**
         * Process material consumption (issue from warehouse for production).
         *
         * <p>
         * <b>Critical Race Condition Prevention:</b> Uses <b>PESSIMISTIC_WRITE lock</b>
         * (SELECT ... FOR UPDATE) to acquire exclusive row lock immediately. This
         * prevents
         * the classic race condition where two concurrent requests both read the same
         * stale
         * quantity, both pass the availability check, and both deduct, resulting in
         * negative stock.
         *
         * <p>
         * Two concurrent ISSUE requests for the same inventory:
         * 
         * <pre>
         * Thread 1: Read qty=100, check qty >= 50 ✓, deduct → qty=50
         * Thread 2: Read qty=100, check qty >= 60 ✓, deduct → qty=40 (WRONG! Should be -10)
         * </pre>
         * 
         * With pessimistic lock, Thread 2 waits for Thread 1's lock release, so it
         * reads qty=50
         * and correctly validates/fails if requested quantity exceeds available.
         *
         * @param req            Issue request (material, warehouse, quantity)
         * @param idempotencyKey Unique key for idempotent processing
         * @param username       User performing the operation
         * @return Transaction response
         * @throws IllegalStateException if stock unavailable
         */
        @Transactional
        public InventoryTransactionResponse processIssue(IssueRequest req, String idempotencyKey, String username) {
                Optional<InventoryTransaction> existing = trxRepo.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent())
                        return InventoryTransactionResponse.from(existing.get());

                Material material = getMaterial(req.getMaterialId());
                Warehouse warehouse = getWarehouse(req.getWarehouseId());

                // PESSIMISTIC LOCK: Acquire exclusive row lock immediately via SELECT ... FOR
                // UPDATE
                Inventory inventory = inventoryRepo
                                .findByMaterialIdAndWarehouseIdForUpdatePessimistic(material.getId(), warehouse.getId())
                                .orElseThrow(() -> new IllegalStateException(
                                                "No stock available for " + material.getMaterialCode()));

                if (inventory.getAvailableQuantity().compareTo(req.getQuantity()) < 0) {
                        throw new IllegalStateException(String.format(
                                        "Insufficient stock: available=%.3f, requested=%.3f",
                                        inventory.getAvailableQuantity(), req.getQuantity()));
                }

                inventory.setQuantity(inventory.getQuantity().subtract(req.getQuantity()));
                inventoryRepo.save(inventory);

                String trxNo = generateTrxNo("ISS");
                InventoryTransaction trx = InventoryTransaction.builder()
                                .trxNo(trxNo).idempotencyKey(idempotencyKey).material(material)
                                .sourceWarehouse(warehouse).trxType(TransactionType.ISSUE)
                                .quantity(req.getQuantity()).referenceNo(req.getWorkOrderNo())
                                .notes(req.getNotes()).performedBy(username).trxTime(LocalDateTime.now()).build();
                trxRepo.save(trx);

                String tenantId = TenantContext.getCurrentTenant();
                auditLogRepo.save(AuditLog.of("INVENTORY_TRANSACTION", trxNo, "ISSUE", username, tenantId,
                                String.format("{\"material\":\"%s\",\"warehouse\":\"%s\",\"qty\":%s,\"workOrder\":\"%s\"}",
                                                material.getMaterialCode(), warehouse.getWarehouseCode(),
                                                req.getQuantity(), req.getWorkOrderNo())));

                outboxService.enqueueIssueEvent(trx, material, warehouse, tenantId);
                issueCounter.increment();
                log.info("Issue processed: trxNo={}, material={}, qty={}, workOrder={}, locked=PESSIMISTIC",
                                trxNo, material.getMaterialCode(), req.getQuantity(), req.getWorkOrderNo());

                return InventoryTransactionResponse.from(trx);
        }

        // ═══════════════════════════════════════════════════════
        // ADJUSTMENT — Manual stock correction
        // ═══════════════════════════════════════════════════════

        @Transactional
        @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
        public InventoryTransactionResponse processAdjustment(AdjustmentRequest req, String idempotencyKey,
                        String username) {
                Optional<InventoryTransaction> existing = trxRepo.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent())
                        return InventoryTransactionResponse.from(existing.get());

                Material material = getMaterial(req.getMaterialId());
                Warehouse warehouse = getWarehouse(req.getWarehouseId());

                Inventory inventory = inventoryRepo
                                .findByMaterialIdAndWarehouseIdForUpdate(material.getId(), warehouse.getId())
                                .orElse(Inventory.builder().material(material).warehouse(warehouse)
                                                .quantity(BigDecimal.ZERO).reservedQuantity(BigDecimal.ZERO).build());

                BigDecimal oldQty = inventory.getQuantity();
                inventory.setQuantity(req.getNewQuantity()); // Set absolute value
                inventoryRepo.save(inventory);

                BigDecimal adjustmentQty = req.getNewQuantity().subtract(oldQty);
                String trxNo = generateTrxNo("ADJ");
                InventoryTransaction trx = InventoryTransaction.builder()
                                .trxNo(trxNo).idempotencyKey(idempotencyKey).material(material)
                                .destinationWarehouse(warehouse).trxType(TransactionType.ADJUSTMENT)
                                .quantity(adjustmentQty.abs()).referenceNo(req.getReferenceNo())
                                .notes(req.getReason()).performedBy(username).trxTime(LocalDateTime.now()).build();
                trxRepo.save(trx);

                String tenantId = TenantContext.getCurrentTenant();
                auditLogRepo.save(AuditLog.of("INVENTORY_TRANSACTION", trxNo, "ADJUSTMENT", username, tenantId,
                                String.format("{\"material\":\"%s\",\"oldQty\":%s,\"newQty\":%s,\"reason\":\"%s\"}",
                                                material.getMaterialCode(), oldQty, req.getNewQuantity(),
                                                req.getReason())));

                outboxService.enqueueAdjustmentEvent(trx, material, warehouse, tenantId);
                adjustmentCounter.increment();
                log.info("Adjustment processed: trxNo={}, material={}, oldQty={}, newQty={}",
                                trxNo, material.getMaterialCode(), oldQty, req.getNewQuantity());

                return InventoryTransactionResponse.from(trx);
        }

        // ═══════════════════════════════════════════════════════
        // Queries
        // ═══════════════════════════════════════════════════════

        @Transactional(readOnly = true)
        public List<Inventory> getStockByMaterial(Long materialId) {
                return inventoryRepo.findByMaterialId(materialId);
        }

        @Transactional(readOnly = true)
        public List<Inventory> getStockByWarehouse(Long warehouseId) {
                return inventoryRepo.findByWarehouseIdWithStock(warehouseId);
        }

        @Transactional(readOnly = true)
        public List<Inventory> getLowStockAlerts() {
                return inventoryRepo.findBelowMinStockLevel();
        }

        // ═══════════════════════════════════════════════════════
        // Helpers
        // ═══════════════════════════════════════════════════════

        private Material getMaterial(Long id) {
                return materialRepo.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Material", id));
        }

        private Warehouse getWarehouse(Long id) {
                return warehouseRepo.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
        }

        /**
         * Thread-safe sequential transaction number.
         * Format: RCP-20240101-000001
         */
        private String generateTrxNo(String prefix) {
                return String.format("%s-%s-%06d", prefix,
                                LocalDateTime.now().format(TRX_DATE_FMT),
                                trxSequence.incrementAndGet() % 1_000_000);
        }
}
