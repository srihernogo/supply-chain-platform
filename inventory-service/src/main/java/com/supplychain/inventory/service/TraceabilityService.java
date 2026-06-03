package com.supplychain.inventory.service;

import com.supplychain.inventory.entity.InventoryTransaction;
import com.supplychain.inventory.repository.InventoryTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceabilityService {

    private final InventoryTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<InventoryTransaction> getMaterialTraceability(Long materialId) {
        log.info("Fetching traceability lineage for materialId={}", materialId);
        return transactionRepository.findByMaterialIdOrderByTrxTimeAsc(materialId);
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransaction> getWarehouseHistory(Long warehouseId, Pageable pageable) {
        log.info("Fetching transaction history for warehouseId={}", warehouseId);
        return transactionRepository.findByWarehouseId(warehouseId, pageable);
    }

    @Transactional(readOnly = true)
    public List<InventoryTransaction> getSupplierTraceability(String supplierCode) {
        log.info("Fetching traceability lineage for supplierCode={}", supplierCode);
        return transactionRepository.findBySupplierCodeOrderByTrxTimeAsc(supplierCode);
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransaction> getTransactionsInTimeRange(LocalDateTime from, LocalDateTime to, Pageable pageable) {
        log.info("Fetching transactions from={} to={}", from, to);
        return transactionRepository.findByTimeRange(from, to, pageable);
    }
}
