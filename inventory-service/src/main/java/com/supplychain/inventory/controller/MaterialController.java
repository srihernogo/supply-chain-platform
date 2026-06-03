package com.supplychain.inventory.controller;

import com.supplychain.common.dto.ApiResponse;
import com.supplychain.inventory.entity.Material;
import com.supplychain.inventory.service.MaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Material>> create(@Valid @RequestBody Material material) {
        Material created = materialService.createMaterial(material);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Material created successfully", created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<Material>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(materialService.getMaterialById(id)));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<Material>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(materialService.getMaterialByCode(code)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE','AUDITOR')")
    public ResponseEntity<ApiResponse<Page<Material>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(materialService.searchMaterials(keyword, category, pageable)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Material>> update(@PathVariable Long id, @Valid @RequestBody Material updated) {
        return ResponseEntity.ok(ApiResponse.ok("Material updated successfully", materialService.updateMaterial(id, updated)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        materialService.deactivateMaterial(id);
        return ResponseEntity.ok(ApiResponse.ok("Material deactivated successfully", null));
    }
}
