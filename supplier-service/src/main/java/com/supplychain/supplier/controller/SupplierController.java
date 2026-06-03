package com.supplychain.supplier.controller;

import com.supplychain.common.dto.ApiResponse;
import com.supplychain.supplier.dto.SupplierRequest;
import com.supplychain.supplier.dto.SupplierResponse;
import com.supplychain.supplier.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Supplier REST controller.
 *
 * <p>Base path: /api/suppliers
 *
 * <p>Role access:
 * <ul>
 *   <li>GET  — ADMIN, WAREHOUSE, AUDITOR</li>
 *   <li>POST/PUT — ADMIN</li>
 *   <li>DELETE — ADMIN</li>
 *   <li>Status management — ADMIN</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    // ─── GET ──────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<Page<SupplierResponse>>> searchSuppliers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "supplierName") Pageable pageable) {

        Page<SupplierResponse> result = supplierService.searchSuppliers(keyword, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR','SUPPLIER')")
    public ResponseEntity<ApiResponse<SupplierResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.getSupplierById(id)));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR','SUPPLIER')")
    public ResponseEntity<ApiResponse<SupplierResponse>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.getSupplierByCode(code)));
    }

    // ─── POST ─────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SupplierResponse>> create(
            @Valid @RequestBody SupplierRequest request,
            @RequestHeader("X-Username") String username) {

        SupplierResponse response = supplierService.createSupplier(request, username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Supplier registered successfully", response));
    }

    // ─── PUT ──────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SupplierResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Supplier updated", supplierService.updateSupplier(id, request)));
    }

    // ─── Status Management ────────────────────────────────

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SupplierResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Supplier activated", supplierService.activateSupplier(id)));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SupplierResponse>> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Supplier suspended", supplierService.suspendSupplier(id)));
    }

    @PostMapping("/{id}/blacklist")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SupplierResponse>> blacklist(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Supplier blacklisted", supplierService.blacklistSupplier(id)));
    }

    // ─── DELETE ───────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.ok(ApiResponse.ok("Supplier deleted", null));
    }
}
