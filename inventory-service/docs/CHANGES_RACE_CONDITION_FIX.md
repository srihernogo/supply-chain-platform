# Race Condition Fix Summary

## Perubahan yang Dilakukan

### 1. InventoryRepository.java
**Lokasi**: `src/main/java/com/supplychain/inventory/repository/InventoryRepository.java`

**Penambahan**:
- Method baru: `findByMaterialIdAndWarehouseIdForUpdatePessimistic()`
- Lock type: `LockModeType.PESSIMISTIC_WRITE` 
- Menggunakan SQL: `SELECT ... FOR UPDATE`
- Dokumentasi lengkap tentang use case dan mengapa perlu pessimistic locking

```java
/**
 * Find with PESSIMISTIC lock (SELECT ... FOR UPDATE) — acquires exclusive row lock immediately.
 * Use this for write-heavy operations (especially issue/deduction) to prevent race conditions
 * entirely instead of retrying on conflicts. More efficient for high-concurrency scenarios.
 */
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE i.material.id = :materialId AND i.warehouse.id = :warehouseId")
Optional<Inventory> findByMaterialIdAndWarehouseIdForUpdatePessimistic(
    @Param("materialId") Long materialId,
    @Param("warehouseId") Long warehouseId);
```

---

### 2. InventoryService.java
**Lokasi**: `src/main/java/com/supplychain/inventory/service/InventoryService.java`

#### A. Dokumentasi Class (Updated)
- Penjelasan strategi race condition prevention
- Dual-locking approach: pessimistic + optimistic
- Breakdown per operasi type (ISSUE, TRANSFER, RECEIPT, ADJUSTMENT)

#### B. Method: `processIssue()` (Modified)
**Perubahan**:
- Hapus: `@Retryable` decorator (tidak perlu retry dengan pessimistic lock)
- Ganti: `findByMaterialIdAndWarehouseIdForUpdate()` → `findByMaterialIdAndWarehouseIdForUpdatePessimistic()`
- Tambah: Dokumentasi detail tentang race condition scenario dan cara pessimistic lock mencegahnya
- Tambah: Log message dengan indikasi locking strategy

**Kode Sebelum**:
```java
@Transactional
@Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3,
           backoff = @Backoff(delay = 100, multiplier = 2))
public InventoryTransactionResponse processIssue(IssueRequest req, String idempotencyKey, String username) {
    // ...
    Inventory inventory = inventoryRepo
        .findByMaterialIdAndWarehouseIdForUpdate(material.getId(), warehouse.getId())
        .orElseThrow(...);
    // ...
}
```

**Kode Sesudah**:
```java
@Transactional
public InventoryTransactionResponse processIssue(IssueRequest req, String idempotencyKey, String username) {
    // ...
    // PESSIMISTIC LOCK: Acquire exclusive row lock immediately via SELECT ... FOR UPDATE
    Inventory inventory = inventoryRepo
        .findByMaterialIdAndWarehouseIdForUpdatePessimistic(material.getId(), warehouse.getId())
        .orElseThrow(...);
    // ...
    log.info("Issue processed: ..., locked=PESSIMISTIC", ...);
}
```

#### C. Method: `processTransfer()` (Modified)
**Perubahan**:
- **Source warehouse**: Ganti ke pessimistic lock (karena ada deduction)
  ```java
  Inventory sourceInventory = inventoryRepo
      .findByMaterialIdAndWarehouseIdForUpdatePessimistic(material.getId(), sourceWh.getId())
      .orElseThrow(...);
  ```

- **Destination warehouse**: Tetap optimistic lock dengan retry (karena addition, lebih aman)
  ```java
  Inventory destInventory = inventoryRepo
      .findByMaterialIdAndWarehouseIdForUpdate(material.getId(), destWh.getId())
      .orElse(...);
  ```

- Tambah: Dokumentasi tentang hybrid locking strategy
- Tambah: Log message dengan indikasi locking per warehouse

---

### 3. Database Schema (Existing - No Changes)
**Lokasi**: `src/main/resources/db/migration/V1__init_inventory.sql`

**Status**: ✅ Sudah menggunakan `version BIGINT` untuk optimistic locking

```sql
CREATE TABLE inventory (
    ...
    version BIGINT NOT NULL DEFAULT 0,  -- Already present for optimistic locking
    ...
);
```

---

## Strategi Locking yang Sekarang Diterapkan

| Operasi | Method | Lock Type | Strategy | Alasan |
|---------|--------|-----------|----------|--------|
| **RECEIPT** | `processReceipt()` | OPTIMISTIC + Retry | Tambah stok | Low contention, throughput tinggi |
| **ISSUE** | `processIssue()` | **PESSIMISTIC** | Kurangi stok | **HIGH CONCURRENCY**, akurasi KRITIS |
| **TRANSFER** | `processTransfer()` | **PESSIMISTIC** (source) + OPTIMISTIC (dest) | Hybrid | Deduction critical, addition safe |
| **ADJUSTMENT** | `processAdjustment()` | OPTIMISTIC + Retry | Manual correction | Low frequency, retry acceptable |

---

## Mengatasi Race Condition

### Sebelum Perbaikan ❌

**Skenario**:
```
inventory.quantity = 100

Thread A: READ qty=100, validate qty>=50✓, deduct → qty=50
Thread B: READ qty=100 (STALE!), validate qty>=60✓, deduct → qty=40 (WRONG!)

Result: qty=40 (should be -10 or reject B)
```

### Sesudah Perbaikan ✅

**Dengan Pessimistic Lock (SELECT ... FOR UPDATE)**:
```
inventory.quantity = 100

Thread A: LOCK row FOR UPDATE, read qty=100, validate qty>=50✓, deduct → qty=50, UNLOCK
Thread B: WAIT untuk lock, baru LOCK row, read qty=50, validate qty>=60? ✗ FAIL
          → IllegalStateException: Insufficient stock

Result: qty=50 (correct!) + B rejected dengan proper error
```

---

## Compatibility & Performance

### Backward Compatibility
✅ **100% backward compatible**
- Existing code tetap berfungsi
- Method lama `findByMaterialIdAndWarehouseIdForUpdate()` masih ada
- RECEIPT & ADJUSTMENT tetap menggunakan optimistic locking
- Hanya ISSUE & TRANSFER (source) yang upgraded ke pessimistic

### Performance Impact
- ✅ **ISSUE**: Slightly slower (wait for lock), tapi **akurat** (no retry overhead)
- ✅ **TRANSFER**: Negligible (source adalah bottleneck, destination optimistic)
- ✅ **RECEIPT**: No change (tetap optimistic)
- ✅ **ADJUSTMENT**: No change (tetap optimistic)

### Scalability
- ✅ **Low contention**: Pessimistic lock minimal overhead
- ✅ **High contention**: Pessimistic lock lebih efisien (no retry storms)
- ✅ **Database**: Tidak ada perubahan schema, lock adalah feature standard PostgreSQL

---

## Verifikasi & Testing

### Unit Tests yang Harus Dijalankan
```bash
# Run existing tests
mvn test -Dtest=InventoryServiceTest

# Run race condition scenario test
mvn test -Dtest=InventoryServiceRaceConditionTest

# Run integration test dengan concurrent requests
mvn test -Dtest=InventoryServiceConcurrencyTest
```

### Manual Testing (Load Test)
```bash
# Install Apache Bench
# Windows: choco install ab
# Linux: apt-get install apache2-utils

# Simulate 50 concurrent requests
ab -n 1000 -c 50 \
  -p payload.json \
  -T application/json \
  http://localhost:8080/api/inventory/issue
```

### Monitoring Queries

**PostgreSQL - Check untuk LOCK**:
```sql
-- Lihat locks yang sedang aktif
SELECT * FROM pg_locks WHERE NOT granted;

-- Lihat transaksi yang menunggu lock
SELECT * FROM pg_stat_activity WHERE state = 'idle in transaction';

-- Lihat inventory rows yang di-lock
SELECT l.* FROM pg_locks l 
JOIN pg_stat_activity a ON l.pid = a.pid 
WHERE relation::regclass::text = 'public.inventory';
```

---

## Files Modified

```
inventory-service/
├── src/main/java/com/supplychain/inventory/
│   ├── repository/
│   │   └── InventoryRepository.java              [MODIFIED] + pessimistic method
│   └── service/
│       └── InventoryService.java                 [MODIFIED] + better docs + pessimistic usage
├── src/main/resources/db/migration/
│   └── V1__init_inventory.sql                    [NO CHANGE] ✓ already has version column
└── RACE_CONDITION_PREVENTION.md                  [NEW] detailed explanation
```

---

## Next Steps (Optional Enhancement)

1. **Add metrics** untuk track pessimistic lock wait time
   ```java
   // Di InventoryService
   @Timed("inventory.lock.pessimistic.acquire")
   public InventoryTransactionResponse processIssue(...) { ... }
   ```

2. **Add integration test** untuk concurrent issue requests
3. **Monitor di production** untuk validate performance impact
4. **Consider ReadUncommitted** untuk high-volume read scenarios
5. **Implement circuit breaker** jika lock timeout terlalu sering

---

## Referensi & Resources

- [JPA Locking Strategies](https://www.baeldung.com/jpa-pessimistic-locking)
- [PostgreSQL SELECT ... FOR UPDATE](https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE)
- [Optimistic Locking Pattern](https://en.wikipedia.org/wiki/Optimistic_concurrency_control)
- [Pessimistic Locking Pattern](https://en.wikipedia.org/wiki/Pessimistic_concurrency_control)
