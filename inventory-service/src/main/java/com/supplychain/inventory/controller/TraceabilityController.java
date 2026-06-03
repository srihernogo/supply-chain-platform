package com.supplychain.inventory.controller;

import com.supplychain.common.dto.ApiResponse;
import com.supplychain.inventory.entity.InventoryTransaction;
import com.supplychain.inventory.service.TraceabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/traceability")
@RequiredArgsConstructor
public class TraceabilityController {

    private final TraceabilityService traceabilityService;

    @GetMapping("/material/{materialId}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<List<InventoryTransaction>>> getMaterialTraceability(@PathVariable Long materialId) {
        return ResponseEntity.ok(ApiResponse.ok(traceabilityService.getMaterialTraceability(materialId)));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<Page<InventoryTransaction>>> getWarehouseHistory(
            @PathVariable Long warehouseId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(traceabilityService.getWarehouseHistory(warehouseId, pageable)));
    }

    @GetMapping("/supplier/{supplierCode}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<List<InventoryTransaction>>> getSupplierTraceability(@PathVariable String supplierCode) {
        return ResponseEntity.ok(ApiResponse.ok(traceabilityService.getSupplierTraceability(supplierCode)));
    }

    @GetMapping("/range")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<Page<InventoryTransaction>>> getTransactionsInTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(traceabilityService.getTransactionsInTimeRange(from, to, pageable)));
    }
}
