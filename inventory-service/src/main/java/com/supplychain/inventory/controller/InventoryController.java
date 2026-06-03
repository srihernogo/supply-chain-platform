package com.supplychain.inventory.controller;

import com.supplychain.common.dto.ApiResponse;
import com.supplychain.inventory.dto.*;
import com.supplychain.inventory.entity.Inventory;
import com.supplychain.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/receipt")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> receipt(
            @Valid @RequestBody ReceiptRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Username", defaultValue = "user") String username) {

        InventoryTransactionResponse response = inventoryService.processReceipt(request, idempotencyKey, username);
        return ResponseEntity.ok(ApiResponse.ok("Material receipt processed successfully", response));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Username", defaultValue = "user") String username) {

        InventoryTransactionResponse response = inventoryService.processTransfer(request, idempotencyKey, username);
        return ResponseEntity.ok(ApiResponse.ok("Material transfer processed successfully", response));
    }

    @PostMapping("/issue")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE')")
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> issue(
            @Valid @RequestBody IssueRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Username", defaultValue = "user") String username) {

        InventoryTransactionResponse response = inventoryService.processIssue(request, idempotencyKey, username);
        return ResponseEntity.ok(ApiResponse.ok("Material issuance processed successfully", response));
    }

    @PostMapping("/adjustment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> adjustment(
            @Valid @RequestBody AdjustmentRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Username", defaultValue = "user") String username) {

        InventoryTransactionResponse response = inventoryService.processAdjustment(request, idempotencyKey, username);
        return ResponseEntity.ok(ApiResponse.ok("Inventory adjustment processed successfully", response));
    }

    @GetMapping("/material/{materialId}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<List<Inventory>>> getStockByMaterial(@PathVariable Long materialId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getStockByMaterial(materialId)));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<List<Inventory>>> getStockByWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getStockByWarehouse(warehouseId)));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<List<Inventory>>> getLowStockAlerts() {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getLowStockAlerts()));
    }
}
