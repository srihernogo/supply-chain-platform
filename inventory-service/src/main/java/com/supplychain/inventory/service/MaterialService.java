package com.supplychain.inventory.service;

import com.supplychain.common.exception.ResourceNotFoundException;
import com.supplychain.common.tenant.TenantContext;
import com.supplychain.inventory.entity.Material;
import com.supplychain.inventory.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;

    @Transactional
    public Material createMaterial(Material material) {
        if (materialRepository.existsByMaterialCode(material.getMaterialCode())) {
            throw new IllegalArgumentException("Material code already exists: " + material.getMaterialCode());
        }
        material.setActive(true);
        log.info("Creating material: code={}, tenant={}", material.getMaterialCode(), TenantContext.getCurrentTenant());
        return materialRepository.save(material);
    }

    @Transactional(readOnly = true)
    public Material getMaterialById(Long id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Material", id));
    }

    @Transactional(readOnly = true)
    public Material getMaterialByCode(String code) {
        return materialRepository.findByMaterialCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Material not found with code: " + code));
    }

    @Transactional(readOnly = true)
    public Page<Material> searchMaterials(String keyword, String category, Pageable pageable) {
        return materialRepository.search(keyword, category, pageable);
    }

    @Transactional
    public Material updateMaterial(Long id, Material updated) {
        Material existing = getMaterialById(id);
        existing.setMaterialName(updated.getMaterialName());
        existing.setCategory(updated.getCategory());
        existing.setUnit(updated.getUnit());
        existing.setMinStockLevel(updated.getMinStockLevel());
        return materialRepository.save(existing);
    }

    @Transactional
    public void deactivateMaterial(Long id) {
        Material existing = getMaterialById(id);
        existing.setActive(false);
        materialRepository.save(existing);
        log.info("Deactivated material: id={}", id);
    }
}
