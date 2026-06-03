package com.supplychain.inventory.controller;

import com.supplychain.common.dto.ApiResponse;
import com.supplychain.inventory.entity.Warehouse;
import com.supplychain.inventory.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Warehouse>> create(@Valid @RequestBody Warehouse warehouse) {
        Warehouse created = warehouseService.createWarehouse(warehouse);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Warehouse created successfully", created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<Warehouse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.getWarehouseById(id)));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<Warehouse>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.getWarehouseByCode(code)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<List<Warehouse>>> getAllActive() {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.getAllActiveWarehouses()));
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<List<Warehouse>>> getByType(@PathVariable Warehouse.WarehouseType type) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.getWarehousesByType(type)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Warehouse>> update(@PathVariable Long id, @Valid @RequestBody Warehouse updated) {
        return ResponseEntity.ok(ApiResponse.ok("Warehouse updated successfully", warehouseService.updateWarehouse(id, updated)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        warehouseService.deactivateWarehouse(id);
        return ResponseEntity.ok(ApiResponse.ok("Warehouse deactivated successfully", null));
    }
}
