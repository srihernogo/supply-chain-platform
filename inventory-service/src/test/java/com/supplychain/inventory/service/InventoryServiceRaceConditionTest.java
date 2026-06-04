package com.supplychain.inventory.service;

import com.supplychain.inventory.dto.IssueRequest;
import com.supplychain.inventory.entity.Inventory;
import com.supplychain.inventory.entity.Material;
import com.supplychain.inventory.entity.Warehouse;
import com.supplychain.inventory.repository.InventoryRepository;
import com.supplychain.inventory.repository.MaterialRepository;
import com.supplychain.inventory.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Race Condition Prevention Test Suite.
 *
 * Tests verify that pessimistic locking on ISSUE operations prevents the
 * classic
 * "lost update" problem where two concurrent requests can cause stock
 * inaccuracy.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("InventoryService Race Condition Prevention Tests")
public class InventoryServiceRaceConditionTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    private Material testMaterial;
    private Warehouse testWarehouse;
    private Inventory testInventory;

    @BeforeEach
    @Transactional
    void setup() {
        // Create test material
        testMaterial = materialRepository.save(Material.builder()
                .materialCode("TEST-RACE-001")
                .materialName("Test Material for Race Condition")
                .category("TEST")
                .minStockLevel(0)
                .active(true)
                .build());

        // Create test warehouse
        testWarehouse = warehouseRepository.save(Warehouse.builder()
                .warehouseCode("WH-RACE-TEST")
                .warehouseName("Race Condition Test Warehouse")
                .location("Test Location")
                .active(true)
                .build());

        // Create initial inventory with 100 units
        testInventory = inventoryRepository.save(Inventory.builder()
                .material(testMaterial)
                .warehouse(testWarehouse)
                .quantity(new BigDecimal("100"))
                .reservedQuantity(BigDecimal.ZERO)
                .build());
    }

    @Test
    @DisplayName("Sequential Issue Operations Should Succeed and Update Correctly")
    @Transactional
    void testSequentialIssueOperations() {
        // First issue: 30 units
        IssueRequest req1 = IssueRequest.builder()
                .materialId(testMaterial.getId())
                .warehouseId(testWarehouse.getId())
                .quantity(new BigDecimal("30"))
                .workOrderNo("WO-001")
                .notes("First issue")
                .build();

        inventoryService.processIssue(req1, "idempotency-seq-1", "user1");

        // Second issue: 40 units
        IssueRequest req2 = IssueRequest.builder()
                .materialId(testMaterial.getId())
                .warehouseId(testWarehouse.getId())
                .quantity(new BigDecimal("40"))
                .workOrderNo("WO-002")
                .notes("Second issue")
                .build();

        inventoryService.processIssue(req2, "idempotency-seq-2", "user2");

        // Verify final quantity: 100 - 30 - 40 = 30
        Inventory updated = inventoryRepository.findById(testInventory.getId()).orElseThrow();
        assertEquals(new BigDecimal("30"), updated.getQuantity(),
                "After sequential issues of 30 and 40, quantity should be 30");
    }

    @Test
    @DisplayName("Concurrent Issue Operations Should Prevent Lost Update (Race Condition Prevention)")
    void testConcurrentIssueRaceConditionPrevention() throws InterruptedException {
        // Initial inventory: 100 units
        // Thread 1: Issue 60 units
        // Thread 2: Issue 50 units (should FAIL because after Thread 1, only 40 remain)

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(2); // Synchronize thread start
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<Exception> capturedError = new AtomicReference<>();

        // Thread 1: Issue 60 units (should succeed)
        Runnable task1 = () -> {
            try {
                startLatch.countDown();
                startLatch.await(); // Wait for both threads to be ready

                IssueRequest req = IssueRequest.builder()
                        .materialId(testMaterial.getId())
                        .warehouseId(testWarehouse.getId())
                        .quantity(new BigDecimal("60"))
                        .workOrderNo("WO-CONCURRENT-1")
                        .notes("Concurrent issue 1")
                        .build();

                inventoryService.processIssue(req, "idempotency-concurrent-1", "user-thread1");
                successCount.incrementAndGet();
                System.out.println("✓ Thread 1 succeeded: Issued 60 units");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                capturedError.set(e);
                System.out.println("✗ Thread 1 failed: " + e.getMessage());
            }
        };

        // Thread 2: Issue 50 units (should FAIL with insufficient stock)
        Runnable task2 = () -> {
            try {
                startLatch.countDown();
                startLatch.await(); // Wait for both threads to be ready

                IssueRequest req = IssueRequest.builder()
                        .materialId(testMaterial.getId())
                        .warehouseId(testWarehouse.getId())
                        .quantity(new BigDecimal("50"))
                        .workOrderNo("WO-CONCURRENT-2")
                        .notes("Concurrent issue 2")
                        .build();

                inventoryService.processIssue(req, "idempotency-concurrent-2", "user-thread2");
                successCount.incrementAndGet();
                System.out.println("✓ Thread 2 succeeded: Issued 50 units");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IllegalStateException e) {
                failureCount.incrementAndGet();
                System.out.println("✓ Thread 2 correctly failed with: " + e.getMessage());
                // This is expected!

            } catch (Exception e) {
                failureCount.incrementAndGet();
                capturedError.set(e);
                System.out.println("✗ Thread 2 failed with unexpected error: " + e.getMessage());
            }
        };

        executor.submit(task1);
        executor.submit(task2);

        executor.shutdown();
        boolean completed = executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(completed, "Executor did not complete within timeout");

        // Verify results
        assertEquals(1, successCount.get(), "Exactly 1 operation should succeed");
        assertEquals(1, failureCount.get(), "Exactly 1 operation should fail (insufficient stock)");

        // Verify final quantity: 100 - 60 = 40 (only the first request succeeded)
        Inventory updated = inventoryRepository.findById(testInventory.getId()).orElseThrow();
        assertEquals(new BigDecimal("40"), updated.getQuantity(),
                "After concurrent issue attempts (60 + 50), only 60 should have been deducted, " +
                        "leaving 40 units. The 50-unit request should have been rejected due to insufficient stock.");
    }

    @Test
    @DisplayName("Concurrent Issue Operations - Both Succeed When Stock is Sufficient")
    void testConcurrentIssueWhenStockSufficient() throws InterruptedException {
        // Initial inventory: 100 units
        // Thread 1: Issue 40 units (should succeed)
        // Thread 2: Issue 30 units (should succeed)
        // Final expected: 100 - 40 - 30 = 30

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        Runnable task1 = () -> {
            try {
                startLatch.countDown();
                startLatch.await();

                IssueRequest req = IssueRequest.builder()
                        .materialId(testMaterial.getId())
                        .warehouseId(testWarehouse.getId())
                        .quantity(new BigDecimal("40"))
                        .workOrderNo("WO-BOTH-1")
                        .build();

                inventoryService.processIssue(req, "idempotency-both-1", "user1");
                successCount.incrementAndGet();
                System.out.println("✓ Thread 1 succeeded: Issued 40 units");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable task2 = () -> {
            try {
                startLatch.countDown();
                startLatch.await();

                IssueRequest req = IssueRequest.builder()
                        .materialId(testMaterial.getId())
                        .warehouseId(testWarehouse.getId())
                        .quantity(new BigDecimal("30"))
                        .workOrderNo("WO-BOTH-2")
                        .build();

                inventoryService.processIssue(req, "idempotency-both-2", "user2");
                successCount.incrementAndGet();
                System.out.println("✓ Thread 2 succeeded: Issued 30 units");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        executor.submit(task1);
        executor.submit(task2);

        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(2, successCount.get(), "Both operations should succeed");

        Inventory updated = inventoryRepository.findById(testInventory.getId()).orElseThrow();
        assertEquals(new BigDecimal("30"), updated.getQuantity(),
                "After issuing 40 and 30 units, should have 30 left (100 - 40 - 30)");
    }

    @Test
    @DisplayName("Concurrent Issue Operations - High Concurrency Stress Test (10 threads)")
    void testHighConcurrencyStressTest() throws InterruptedException {
        // 10 threads, each trying to issue 8 units
        // Initial: 100 units
        // Expected: Some will succeed, some will fail, total deducted: at most 100
        // 100 / 8 = 12.5, so max 12 should succeed, 13+ should fail

        int numThreads = 10;
        int quantityPerThread = 8;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> successfulTrxIds = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    startLatch.countDown();
                    startLatch.await();

                    IssueRequest req = IssueRequest.builder()
                            .materialId(testMaterial.getId())
                            .warehouseId(testWarehouse.getId())
                            .quantity(new BigDecimal(quantityPerThread))
                            .workOrderNo("WO-STRESS-" + threadNum)
                            .build();

                    inventoryService.processIssue(req, "idempotency-stress-" + threadNum, "user-" + threadNum);
                    successCount.incrementAndGet();
                    System.out.println("✓ Thread " + threadNum + " succeeded");

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IllegalStateException e) {
                    failureCount.incrementAndGet();
                    System.out.println("✓ Thread " + threadNum + " correctly rejected (insufficient stock)");
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("✗ Thread " + threadNum + " failed: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

        int totalRequested = numThreads * quantityPerThread;
        int totalDeducted = successCount.get() * quantityPerThread;
        int expectedRemaining = 100 - totalDeducted;

        System.out.println("\n=== STRESS TEST RESULTS ===");
        System.out.println("Total threads: " + numThreads);
        System.out.println("Quantity per thread: " + quantityPerThread);
        System.out.println("Successful operations: " + successCount.get());
        System.out.println("Failed operations: " + failureCount.get());
        System.out.println("Total requested: " + totalRequested);
        System.out.println("Total deducted: " + totalDeducted);
        System.out.println("Expected remaining: " + expectedRemaining);

        Inventory updated = inventoryRepository.findById(testInventory.getId()).orElseThrow();

        // Verify: total deducted should never exceed initial quantity
        assertTrue(totalDeducted <= 100, "Total deducted should not exceed initial stock of 100");

        // Verify: final quantity should match calculations
        assertEquals(new BigDecimal(expectedRemaining), updated.getQuantity(),
                "Final quantity should be " + expectedRemaining + " (100 - " + totalDeducted + ")");

        // Verify: version was incremented for each successful operation
        assertTrue(updated.getVersion() >= 1, "Version should have been incremented");

        System.out.println("✓ All assertions passed. Stock integrity maintained under high concurrency.");
    }

    @Test
    @DisplayName("Idempotency - Duplicate Requests Should Not Cause Double Deduction")
    void testIdempotencyPreventsDuplicateDeduction() {
        String idempotencyKey = "idempotency-dup-test";

        IssueRequest req = IssueRequest.builder()
                .materialId(testMaterial.getId())
                .warehouseId(testWarehouse.getId())
                .quantity(new BigDecimal("20"))
                .workOrderNo("WO-DUP")
                .build();

        // First request
        inventoryService.processIssue(req, idempotencyKey, "user1");

        // Second request with same idempotency key (should be ignored)
        inventoryService.processIssue(req, idempotencyKey, "user1");

        // Verify quantity deducted only once
        Inventory updated = inventoryRepository.findById(testInventory.getId()).orElseThrow();
        assertEquals(new BigDecimal("80"), updated.getQuantity(),
                "Duplicate request should not cause double deduction. Only 20 should be deducted.");
    }
}
