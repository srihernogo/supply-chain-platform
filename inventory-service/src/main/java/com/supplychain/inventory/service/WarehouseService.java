package com.supplychain.inventory.service;

import com.supplychain.common.exception.ResourceNotFoundException;
import com.supplychain.common.tenant.TenantContext;
import com.supplychain.inventory.entity.Warehouse;
import com.supplychain.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Transactional
    public Warehouse createWarehouse(Warehouse warehouse) {
        if (warehouseRepository.existsByWarehouseCode(warehouse.getWarehouseCode())) {
            throw new IllegalArgumentException("Warehouse code already exists: " + warehouse.getWarehouseCode());
        }
        warehouse.setActive(true);
        log.info("Creating warehouse: code={}, tenant={}", warehouse.getWarehouseCode(), TenantContext.getCurrentTenant());
        return warehouseRepository.save(warehouse);
    }

    @Transactional(readOnly = true)
    public Warehouse getWarehouseById(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
    }

    @Transactional(readOnly = true)
    public Warehouse getWarehouseByCode(String code) {
        return warehouseRepository.findByWarehouseCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found with code: " + code));
    }

    @Transactional(readOnly = true)
    public List<Warehouse> getAllActiveWarehouses() {
        return warehouseRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<Warehouse> getWarehousesByType(Warehouse.WarehouseType type) {
        return warehouseRepository.findByWarehouseType(type);
    }

    @Transactional
    public Warehouse updateWarehouse(Long id, Warehouse updated) {
        Warehouse existing = getWarehouseById(id);
        existing.setWarehouseName(updated.getWarehouseName());
        existing.setWarehouseType(updated.getWarehouseType());
        existing.setLocation(updated.getLocation());
        return warehouseRepository.save(existing);
    }

    @Transactional
    public void deactivateWarehouse(Long id) {
        Warehouse existing = getWarehouseById(id);
        existing.setActive(false);
        warehouseRepository.save(existing);
        log.info("Deactivated warehouse: id={}", id);
    }
}
