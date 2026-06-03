package com.supplychain.inventory.repository;

import com.supplychain.inventory.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByWarehouseCode(String warehouseCode);
    boolean existsByWarehouseCode(String warehouseCode);
    List<Warehouse> findByActiveTrue();
    List<Warehouse> findByWarehouseType(Warehouse.WarehouseType type);
}
