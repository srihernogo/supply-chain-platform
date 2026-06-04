# Testing Race Condition Prevention

Panduan lengkap untuk menjalankan dan memvalidasi race condition prevention pada inventory service.

## Daftar Isi
1. [Unit Tests](#unit-tests)
2. [Integration Tests](#integration-tests)
3. [Load Tests](#load-tests)
4. [Database Lock Monitoring](#database-lock-monitoring)
5. [Troubleshooting](#troubleshooting)

---

## Unit Tests

### 1. Jalankan Test Race Condition

```bash
cd /path/to/supply-chain-platform/inventory-service

# Run the specific race condition test suite
mvn test -Dtest=InventoryServiceRaceConditionTest -v

# Or run all inventory service tests
mvn test -Dtest=InventoryService* -v
```

### 2. Test Cases yang Tersedia

#### A. Sequential Issue Operations ✓
**Nama**: `testSequentialIssueOperations()`
**Tujuan**: Verifikasi operasi issue berurutan berfungsi dengan benar
**Scenario**:
- Initial stock: 100 units
- Thread 1: Issue 30 units
- Thread 2: Issue 40 units
- Expected: Final stock = 30

**Hasil Sukses**:
```
✓ After sequential issues of 30 and 40, quantity should be 30
```

#### B. Concurrent Issue - Race Condition Prevention ✓✓✓ (CRITICAL TEST)
**Nama**: `testConcurrentIssueRaceConditionPrevention()`
**Tujuan**: VERIFY bahwa pessimistic locking MENCEGAH race condition
**Scenario**:
- Initial stock: 100 units
- Thread 1 & Thread 2 start concurrently (synchronized with CountDownLatch)
- Thread 1: Issue 60 units
- Thread 2: Issue 50 units (akan fail karena insufficient stock)
- Expected: Thread 1 succeeds, Thread 2 fails with IllegalStateException

**Race Condition Behavior (BEFORE FIX)**:
```
Thread 1: READ qty=100, check 100>=60? ✓, prepare to deduct 60
Thread 2: READ qty=100 (STALE!), check 100>=50? ✓, prepare to deduct 50
Thread 1: SAVE qty=40 (100-60)
Thread 2: SAVE qty=50 (100-50)  ← OVERWRITES Thread 1!

Result: qty=50 (WRONG! Should be -10 or rejection)
```

**Fixed Behavior (AFTER PESSIMISTIC LOCK)**:
```
Thread 1: LOCK row FOR UPDATE, READ qty=100, check 100>=60? ✓, deduct, SAVE, UNLOCK
Thread 2: WAIT untuk lock, LOCK row FOR UPDATE, READ qty=40, check 40>=50? ✗ FAIL

Result: qty=40 (CORRECT!) + Thread 2 rejected properly
```

**Hasil Sukses**:
```
✓ Exactly 1 operation should succeed
✓ Exactly 1 operation should fail (insufficient stock)
✓ After concurrent issue attempts (60 + 50), only 60 should have been deducted
```

#### C. Both Concurrent Issues Succeed (Sufficient Stock)
**Nama**: `testConcurrentIssueWhenStockSufficient()`
**Tujuan**: Verifikasi multiple concurrent issues succeed ketika stock mencukupi
**Scenario**:
- Initial stock: 100 units
- Thread 1: Issue 40 units
- Thread 2: Issue 30 units
- Expected: Both succeed, Final = 30

**Hasil Sukses**:
```
✓ Both operations should succeed
✓ After issuing 40 and 30 units, should have 30 left
```

#### D. High Concurrency Stress Test (10 Threads)
**Nama**: `testHighConcurrencyStressTest()`
**Tujuan**: Stress test dengan banyak concurrent threads
**Scenario**:
- Initial stock: 100 units
- 10 threads concurrently, each trying to issue 8 units
- Expected: Max 12 succeed (100/8), rest fail gracefully
- Final stock integrity must be maintained

**Output**:
```
=== STRESS TEST RESULTS ===
Total threads: 10
Quantity per thread: 8
Successful operations: 12
Failed operations: 8 (correctly rejected - insufficient stock)
Total requested: 80
Total deducted: 96 (12 * 8)
Expected remaining: 4

✓ All assertions passed. Stock integrity maintained under high concurrency.
```

#### E. Idempotency Prevention
**Nama**: `testIdempotencyPreventsDuplicateDeduction()`
**Tujuan**: Verifikasi duplicate requests tidak menyebabkan double deduction
**Scenario**:
- Send request dengan idempotency key "dup-test"
- Send request yang sama lagi
- Expected: Stock deducted hanya 1x

**Hasil Sukses**:
```
✓ Duplicate request should not cause double deduction. Only 20 should be deducted.
```

### 3. Expected Output

Ketika menjalankan `mvn test -Dtest=InventoryServiceRaceConditionTest`:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.supplychain.inventory.service.InventoryServiceRaceConditionTest

[INFO] Sequential Issue Operations Should Succeed and Update Correctly ... PASSED
[INFO] ✓ After sequential issues of 30 and 40, quantity should be 30

[INFO] Concurrent Issue Operations Should Prevent Lost Update (Race Condition Prevention) ... PASSED
[INFO] ✓ Thread 1 succeeded: Issued 60 units
[INFO] ✓ Thread 2 correctly failed with: Insufficient stock: available=40.0, requested=50.0

[INFO] Concurrent Issue Operations - Both Succeed When Stock is Sufficient ... PASSED
[INFO] ✓ Thread 1 succeeded: Issued 40 units
[INFO] ✓ Thread 2 succeeded: Issued 30 units

[INFO] Concurrent Issue Operations - High Concurrency Stress Test (10 threads) ... PASSED
[INFO] === STRESS TEST RESULTS ===
[INFO] Total threads: 10
[INFO] Successful operations: 12
[INFO] Failed operations: 8
[INFO] ✓ All assertions passed. Stock integrity maintained under high concurrency.

[INFO] Idempotency - Duplicate Requests Should Not Cause Double Deduction ... PASSED

[INFO] -------------------------------------------------------
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] -------------------------------------------------------
```

---

## Integration Tests

### 1. Full Transaction Flow Test

```bash
# Run all inventory service tests (including integration)
mvn verify -Dtest=InventoryService*
```

### 2. Tenant Isolation + Race Condition

Memastikan race condition prevention bekerja per-tenant (multi-tenant scenario):

```java
@Test
void testRaceConditionPerTenant() throws InterruptedException {
    // Setup: Create same material/warehouse in 2 different tenants
    TenantContext.setCurrentTenant("TENANT-A");
    Material matA = materialRepository.save(...);
    Warehouse whA = warehouseRepository.save(...);
    Inventory invA = inventoryRepository.save(Inventory.builder()...quantity(100)...build());
    
    TenantContext.setCurrentTenant("TENANT-B");
    Material matB = materialRepository.save(...);
    Warehouse whB = warehouseRepository.save(...);
    Inventory invB = inventoryRepository.save(Inventory.builder()...quantity(100)...build());
    
    // Run concurrent issues in both tenants
    // Expected: Both maintain separate stock integrity
}
```

---

## Load Tests

### 1. Apache Bench Load Test

```bash
# Install Apache Bench
# Windows: choco install ab
# macOS: brew install httpd
# Linux: apt-get install apache2-utils

# Start application
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"

# Create JSON payload
cat > /tmp/issue_payload.json << 'EOF'
{
  "materialId": 1,
  "warehouseId": 1,
  "quantity": 5.0,
  "workOrderNo": "WO-LOAD-TEST",
  "notes": "Load test request"
}
EOF

# Run load test: 100 requests, 10 concurrent connections
ab -n 100 -c 10 \
  -p /tmp/issue_payload.json \
  -T application/json \
  http://localhost:8080/api/inventory/issue

# Result analysis:
# - Look for: "Requests per second" (should be healthy)
# - No errors (Failed requests: 0)
# - Response times consistent (no timeout)
```

### 2. Heavy Concurrent Load (50 concurrent)

```bash
ab -n 500 -c 50 \
  -p /tmp/issue_payload.json \
  -T application/json \
  http://localhost:8080/api/inventory/issue
```

**Expected Output**:
```
This is ApacheBench, Version 2.3
Concurrency Level:      50
Time taken for tests:   12.345 seconds
Complete requests:      500
Failed requests:        0
Requests per second:    40.51 [#/sec] (mean)
Time per request:       1234.56 [ms] (mean)
Time per request:       24.69 [ms] (mean, across all concurrent requests)

Percentage of the requests served within a certain time (ms)
  50%    245
  66%    356
  75%    412
  90%    601
  95%    812
  99%   1205
 100%   1523 (longest request)
```

### 3. Continuous Load Test (5 minutes)

```bash
# Run for 5 minutes (300 seconds) with 20 concurrent connections
ab -t 300 -c 20 \
  -p /tmp/issue_payload.json \
  -T application/json \
  http://localhost:8080/api/inventory/issue
```

---

## Database Lock Monitoring

### 1. PostgreSQL: View Active Locks

```sql
-- Connect to database
psql -U postgres -d supply_chain_db

-- View all locks (including waiting locks)
SELECT * FROM pg_locks WHERE NOT granted;

-- View locks on inventory table specifically
SELECT 
    l.locktype,
    l.relation::regclass AS table_name,
    l.mode,
    l.granted,
    a.usename,
    a.query,
    a.state
FROM pg_locks l
JOIN pg_stat_activity a ON l.pid = a.pid
WHERE l.relation::regclass::text LIKE '%inventory%'
ORDER BY a.xact_start DESC;

-- View transactions waiting for locks
SELECT 
    pid,
    usename,
    pg_blocking_pids(pid) AS blocked_by,
    query,
    state,
    wait_event
FROM pg_stat_activity
WHERE pg_blocking_pids(pid)::text != '{}'
ORDER BY wait_event_type DESC;
```

### 2. Monitor Lock Contention During Load Test

```bash
# Terminal 1: Start monitoring
watch -n 1 'psql -U postgres -d supply_chain_db -c "SELECT COUNT(*) as waiting_locks FROM pg_locks WHERE NOT granted;"'

# Terminal 2: Run load test
ab -n 1000 -c 50 -p /tmp/issue_payload.json -T application/json http://localhost:8080/api/inventory/issue

# Expected: waiting_locks should be HIGH (showing pessimistic locks are working)
```

### 3. Check Lock Acquisition Time

```sql
-- Enable slow query log
SET log_min_duration_statement = 100;  -- Log queries > 100ms

-- Query log after running load test
SELECT query, calls, mean_exec_time, max_exec_time
FROM pg_stat_statements
WHERE query LIKE '%FOR UPDATE%'
ORDER BY mean_exec_time DESC;
```

---

## Troubleshooting

### Issue 1: Test Fails with "Deadlock Detected"

**Simptom**:
```
ERROR: could not serialize access due to concurrent update
or
FATAL: deadlock detected
```

**Solusi**:
1. Reduce number of concurrent threads
2. Increase statement timeout: `SET statement_timeout = 5000;`
3. Check transaction isolation level:
   ```sql
   SHOW transaction_isolation;  -- Should be 'read_committed'
   ```

### Issue 2: Test Hangs / Timeout

**Simptom**: Test jalan 30 detik terus timeout

**Penyebab**: Kemungkinan deadlock atau lock tidak dilepas

**Solusi**:
```sql
-- Kill long-running transactions
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE duration > interval '30 seconds';

-- Check for idle transactions holding locks
SELECT * FROM pg_stat_activity WHERE state = 'idle in transaction';
```

### Issue 3: Wrong Lock Type Being Used

**Simptom**: Race condition masih terjadi, atau performa buruk

**Debug**:
```java
// Add logging
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE ...")
Optional<Inventory> find...ForUpdatePessimistic(...) {
    log.info("Acquiring PESSIMISTIC_WRITE lock for material={}, warehouse={}", ...);
    // ...
}
```

Check logs untuk memastikan pessimistic lock digunakan.

### Issue 4: Performance Degradation

**Simptom**: After applying pessimistic lock, throughput drastically drops

**Ini adalah EXPECTED** untuk high-concurrency scenarios, tapi verifikasi:

1. Lock wait time reasonable (< 1 second avg)
   ```sql
   SELECT p.query, EXTRACT(EPOCH FROM (NOW() - p.query_start)) as duration
   FROM pg_stat_activity p
   WHERE state = 'active';
   ```

2. No transaction deadlocks
   ```sql
   SELECT tbl, idx, n_tup_ins, n_tup_upd, n_tup_del FROM pg_stat_user_tables;
   ```

3. Connection pool not exhausted
   ```
   Check in application.properties:
   spring.datasource.hikari.maximum-pool-size=10
   ```

---

## Best Practices for Testing

1. **Always run tests with real database** (not H2 in-memory)
   ```yaml
   # application-test.yml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/test_db
       username: postgres
       password: password
   ```

2. **Use CountDownLatch for thread synchronization**
   ```java
   CountDownLatch latch = new CountDownLatch(2);
   latch.countDown();  // Thread-ready
   latch.await();      // All threads wait here before proceeding
   ```

3. **Monitor metrics during load test**
   ```bash
   # In another terminal, check Prometheus metrics
   curl http://localhost:8080/actuator/metrics/inventory.transaction.issue
   ```

4. **Test with production-like data volumes**
   - Not just 100 units, test with millions
   - Multiple materials and warehouses

---

## Summary

✅ **Run this test suite** untuk verify race condition fix:
```bash
mvn clean test -Dtest=InventoryServiceRaceConditionTest -v
```

Expected: **5/5 tests PASS** = Race condition prevention working correctly ✓

📊 **Verify with load test** untuk production readiness:
```bash
ab -n 500 -c 50 -p payload.json -T application/json http://localhost:8080/api/inventory/issue
```

Expected: **0 failed requests** + **no data integrity issues** ✓

🔒 **Monitor database locks** saat load test untuk memastikan pessimistic locks bekerja:
```sql
SELECT COUNT(*) FROM pg_locks WHERE NOT granted;
```

Expected: **Lock contention visible** = pessimistic locking active ✓
