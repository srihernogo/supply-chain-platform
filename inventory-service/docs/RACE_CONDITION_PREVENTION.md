# Race Condition Prevention Strategy

## Masalah: Race Condition pada Operasi Persediaan

### Skenario Problematik

Ketika dua request pengurangan stok (IssueRequest) masuk secara bersamaan, terjadi lost update:

```
Initial State: inventory.quantity = 100

Thread 1 (Request A):
  1. Read quantity = 100
  2. Check: 100 >= 50 (requested)? ✓ PASS
  3. Calculate: 100 - 50 = 50
  4. (paused by scheduler)

Thread 2 (Request B):
  1. Read quantity = 100 (STALE! Thread 1 hasn't saved yet)
  2. Check: 100 >= 60 (requested)? ✓ PASS
  3. Calculate: 100 - 60 = 40
  4. Save: quantity = 40

Thread 1 (resumes):
  5. Save: quantity = 50

Final State: quantity = 50
Expected:     quantity should be -10 or reject Request B
```

**Dampak**: Stok tercatat tidak akurat, bahkan bisa negatif → Kesalahan dalam manajemen rantai pasokan.

---

## Solusi: Dual-Locking Strategy

### 1. **Pessimistic Locking** (SELECT ... FOR UPDATE) — Untuk Operasi Deduction

Digunakan pada operasi yang **mengurangi stok** (ISSUE, TRANSFER).

#### Implementasi

```java
// Di InventoryRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE i.material.id = :materialId AND i.warehouse.id = :warehouseId")
Optional<Inventory> findByMaterialIdAndWarehouseIdForUpdatePessimistic(
    @Param("materialId") Long materialId,
    @Param("warehouseId") Long warehouseId);

// Di InventoryService.java - processIssue()
@Transactional
public InventoryTransactionResponse processIssue(IssueRequest req, String idempotencyKey, String username) {
    // ... validation ...
    
    // PESSIMISTIC LOCK: Acquire exclusive row lock immediately
    Inventory inventory = inventoryRepo
        .findByMaterialIdAndWarehouseIdForUpdatePessimistic(material.getId(), warehouse.getId())
        .orElseThrow(...);
    
    // Deduction with guaranteed no race condition
    inventory.setQuantity(inventory.getQuantity().subtract(req.getQuantity()));
    inventoryRepo.save(inventory);
    // ...
}
```

#### Keuntungan

- ✅ **Mencegah race condition sejak awal** dengan mengunci baris di database sebelum membacanya
- ✅ **Tidak perlu retry** — jika baris dikunci thread lain, cukup tunggu lock dilepas
- ✅ **Atomic read-check-update** dalam satu transaksi
- ✅ **Ideal untuk operasi write-heavy** atau high-concurrency

#### SQL yang Dijalankan

```sql
-- Hibernate generates:
SELECT * FROM inventory WHERE material_id = ? AND warehouse_id = ? FOR UPDATE;
```

#### Performa

- **Tingkat Throughput**: Lebih rendah jika ada banyak konflik
- **Predictability**: Tinggi — tidak ada retry/rollback
- **Cocok untuk**: Operasi kritis yang harus akurat (ISSUE, TRANSFER)

---

### 2. **Optimistic Locking** (dengan @Version) — Untuk Operasi Addition

Digunakan pada operasi yang **menambah stok** (RECEIPT, ADJUSTMENT).

#### Implementasi

```java
// Di Inventory.java
@Version
@Column(name = "version", nullable = false)
private Long version;

// Di InventoryRepository.java
@Lock(LockModeType.OPTIMISTIC)
@Query("SELECT i FROM Inventory i WHERE i.material.id = :materialId AND i.warehouse.id = :warehouseId")
Optional<Inventory> findByMaterialIdAndWarehouseIdForUpdate(
    @Param("materialId") Long materialId,
    @Param("warehouseId") Long warehouseId);

// Di InventoryService.java - processReceipt()
@Transactional
@Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3,
           backoff = @Backoff(delay = 100, multiplier = 2))
public InventoryTransactionResponse processReceipt(ReceiptRequest req, String idempotencyKey, String username) {
    Inventory inventory = inventoryRepo
        .findByMaterialIdAndWarehouseIdForUpdate(material.getId(), warehouse.getId())
        .orElse(Inventory.builder().quantity(BigDecimal.ZERO).build());
    
    inventory.setQuantity(inventory.getQuantity().add(req.getQuantity()));
    inventoryRepo.save(inventory);  // Throws OptimisticLockException if version mismatch
}
```

#### Database Schema

```sql
CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    material_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quantity DECIMAL(15, 3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,  -- ← Version field
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    CONSTRAINT uq_material_warehouse UNIQUE (material_id, warehouse_id)
);
```

#### Keuntungan

- ✅ **Throughput tinggi** pada low-contention scenario
- ✅ **Tidak perlu lock database** — lebih scalable
- ✅ **Automatic retry** dengan exponential backoff
- ✅ **Cocok untuk addition** yang jarang ada konflik

#### Performa

- **Tingkat Throughput**: Sangat tinggi untuk low-contention
- **Predictability**: Lebih rendah — bisa retry 3x jika ada konflik
- **Cocok untuk**: Operasi addition/adjustment (RECEIPT, ADJUSTMENT)

---

## Tabel Perbandingan

| Aspek | Pessimistic (FOR UPDATE) | Optimistic (@Version + Retry) |
|-------|--------------------------|-------------------------------|
| **Lock Type** | Database lock (exclusive) | Application-level version check |
| **Saat Lock Diakuisisi** | Saat READ (SELECT ... FOR UPDATE) | Saat WRITE (save) |
| **Throughput** | Rendah-sedang | Tinggi |
| **Conflict Handling** | Wait | Retry dengan backoff |
| **Use Case** | Write-heavy, high-concurrency | Read-heavy, low-conflict |
| **Aplikasi di Sistem Ini** | ISSUE, TRANSFER | RECEIPT, ADJUSTMENT |

---

## Strategi Penerapan di Inventory Service

### RECEIPT (Material received from supplier)
```
Operasi: Tambah stok ➕
Lock: OPTIMISTIC (@Version + retry)
Alasan: Addition jarang ada konflik; perlu throughput tinggi
```

### ISSUE (Material issued for production)
```
Operasi: Kurangi stok ➖
Lock: PESSIMISTIC (SELECT ... FOR UPDATE)
Alasan: Deduction paling sering terjadi bersamaan; akurasi KRITIS
```

### TRANSFER (Move between warehouses)
```
Operasi: Kurangi (source) ➖ + Tambah (destination) ➕
Lock: 
  - Source: PESSIMISTIC (kritis, deduction)
  - Destination: OPTIMISTIC (addition, retry)
Alasan: Hybrid approach untuk balance throughput dan akurasi
```

### ADJUSTMENT (Manual stock correction)
```
Operasi: Set nilai absolut
Lock: OPTIMISTIC (@Version + retry)
Alasan: Manual operation, jarang konflik
```

---

## Testing Race Condition

### Simulasi Race Condition (Before Fix)

```java
@Test
@Transactional
public void testConcurrentIssueRaceCondition() throws InterruptedException, ExecutionException {
    // Setup
    Material mat = materialRepo.save(Material.builder().materialCode("TEST-001").build());
    Warehouse wh = warehouseRepo.save(Warehouse.builder().warehouseCode("WH-001").build());
    Inventory inv = inventoryRepo.save(Inventory.builder()
        .material(mat).warehouse(wh)
        .quantity(BigDecimal.valueOf(100))
        .build());
    
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicInteger successCount = new AtomicInteger(0);
    
    // Two concurrent ISSUE requests, each requesting 60 units
    Runnable issueTask = () -> {
        try {
            IssueRequest req = new IssueRequest();
            req.setMaterialId(mat.getId());
            req.setWarehouseId(wh.getId());
            req.setQuantity(BigDecimal.valueOf(60));
            
            inventoryService.processIssue(req, "idempotency-" + System.nanoTime(), "user1");
            successCount.incrementAndGet();
        } catch (Exception e) {
            // Expected: second request should fail
        }
    };
    
    Future<?> future1 = executor.submit(issueTask);
    Future<?> future2 = executor.submit(issueTask);
    
    future1.get();
    future2.get();
    
    // Verify: only 1 should succeed (second should fail with insufficient stock)
    Inventory updated = inventoryRepo.findById(inv.getId()).get();
    assertEquals(40, updated.getQuantity().doubleValue()); // 100 - 60 = 40
    assertEquals(1, successCount.get()); // Only 1 succeeded
}
```

### Verifikasi Fix (After Pessimistic Lock)

```bash
# Run test — harus pass
mvn test -Dtest=InventoryServiceRaceConditionTest

# Jalankan dalam concurrent load test
ab -n 1000 -c 50 http://localhost:8080/api/inventory/issue
```

---

## Database Migration (jika belum ada version column)

```sql
-- V3__add_version_if_not_exists.sql
ALTER TABLE inventory 
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_inventory_version ON inventory(version);
```

---

## Monitoring & Observability

### Prometheus Metrics

```yaml
# Existing metrics tetap digunakan
inventory.transaction.issue         # Total issue transactions
inventory.transaction.transfer      # Total transfer transactions
inventory.transaction.receipt       # Total receipt transactions

# Rekomendasi tambahan:
inventory.lock.pessimistic.wait_ms  # Waktu tunggu untuk pessimistic lock
inventory.lock.optimistic.conflict  # Jumlah OptimisticLockException
inventory.transaction.retry.count   # Jumlah retry due to optimistic lock
```

### Log Patterns

```java
// ISSUE dengan pessimistic lock
log.info("Issue processed: trxNo={}, locked=PESSIMISTIC", trxNo);

// RECEIPT dengan optimistic lock
log.info("Receipt processed: trxNo={}, locked=OPTIMISTIC", trxNo);

// Jika ada retry
log.warn("Retry attempt on optimistic lock: attempt={}, material={}", attempt, materialCode);
```

---

## Kesimpulan

✅ **Sistem sudah dilindungi** dari race condition dengan:
1. **Pessimistic Locking** untuk operasi ISSUE/TRANSFER (deduction)
2. **Optimistic Locking + Retry** untuk operasi RECEIPT/ADJUSTMENT (addition)
3. **Idempotency Keys** untuk mencegah duplikasi request
4. **Audit Logs** untuk traceability

🔒 **Stok akan selalu akurat** bahkan dalam scenario concurrent requests.
