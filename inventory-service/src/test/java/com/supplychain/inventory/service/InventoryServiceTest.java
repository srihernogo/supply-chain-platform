package com.supplychain.inventory.service;

import com.supplychain.common.tenant.TenantContext;
import com.supplychain.inventory.dto.*;
import com.supplychain.inventory.entity.*;
import com.supplychain.inventory.outbox.OutboxService;
import com.supplychain.inventory.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Unit Tests")
class InventoryServiceTest {

    @Mock private InventoryTransactionRepository trxRepo;
    @Mock private InventoryRepository inventoryRepo;
    @Mock private MaterialRepository materialRepo;
    @Mock private WarehouseRepository warehouseRepo;
    @Mock private AuditLogRepository auditLogRepo;
    @Mock private OutboxService outboxService;

    private InventoryService inventoryService;

    private Material material;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("toyota");
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        inventoryService = new InventoryService(
                trxRepo, inventoryRepo, materialRepo, warehouseRepo, auditLogRepo, outboxService, meterRegistry
        );

        material = Material.builder()
                .id(1L)
                .materialCode("MAT-STEE-001")
                .materialName("Steel Coil")
                .unit("COIL")
                .active(true)
                .build();

        warehouse = Warehouse.builder()
                .id(1L)
                .warehouseCode("WH-MAIN")
                .warehouseName("Main Warehouse")
                .warehouseType(Warehouse.WarehouseType.RAW_MATERIAL)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("processReceipt — should process successfully and update stock")
    void processReceipt_success() {
        ReceiptRequest req = ReceiptRequest.builder()
                .materialId(1L)
                .warehouseId(1L)
                .quantity(BigDecimal.TEN)
                .supplierCode("SUP-001")
                .purchaseOrderNo("PO-100")
                .build();

        when(trxRepo.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(materialRepo.findById(1L)).thenReturn(Optional.of(material));
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(inventoryRepo.findByMaterialIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.empty());

        InventoryTransactionResponse response = inventoryService.processReceipt(req, "key-123", "user1");

        assertThat(response).isNotNull();
        assertThat(response.getQuantity()).isEqualByComparingTo("10");
        assertThat(response.getMaterialCode()).isEqualTo("MAT-STEE-001");

        verify(trxRepo).save(any(InventoryTransaction.class));
        verify(inventoryRepo).save(any(Inventory.class));
        verify(auditLogRepo).save(any(AuditLog.class));
        verify(outboxService).enqueueReceiptEvent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("processReceipt — should return existing transaction if duplicate key")
    void processReceipt_duplicateKey() {
        InventoryTransaction existingTrx = InventoryTransaction.builder()
                .id(99L)
                .trxNo("RCP-2026-0001")
                .material(material)
                .destinationWarehouse(warehouse)
                .trxType(InventoryTransaction.TransactionType.RECEIPT)
                .quantity(BigDecimal.TEN)
                .performedBy("user1")
                .build();

        when(trxRepo.findByIdempotencyKey("key-123")).thenReturn(Optional.of(existingTrx));

        InventoryTransactionResponse response = inventoryService.processReceipt(new ReceiptRequest(), "key-123", "user1");

        assertThat(response.getId()).isEqualTo(99L);
        verify(inventoryRepo, never()).save(any());
        verify(outboxService, never()).enqueueReceiptEvent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("processIssue — should throw error when insufficient stock")
    void processIssue_insufficientStock() {
        IssueRequest req = IssueRequest.builder()
                .materialId(1L)
                .warehouseId(1L)
                .quantity(BigDecimal.TEN)
                .build();

        Inventory currentStock = Inventory.builder()
                .material(material)
                .warehouse(warehouse)
                .quantity(BigDecimal.ONE) // only 1 unit
                .reservedQuantity(BigDecimal.ZERO)
                .build();

        when(trxRepo.findByIdempotencyKey("key-123")).thenReturn(Optional.empty());
        when(materialRepo.findById(1L)).thenReturn(Optional.of(material));
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(inventoryRepo.findByMaterialIdAndWarehouseIdForUpdate(1L, 1L)).thenReturn(Optional.of(currentStock));

        assertThatThrownBy(() -> inventoryService.processIssue(req, "key-123", "user1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");
    }
}
