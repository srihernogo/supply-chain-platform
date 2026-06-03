package com.supplychain.inventory.event;

import com.supplychain.common.event.InventoryEvent;
import com.supplychain.inventory.entity.InventoryTransaction;
import com.supplychain.inventory.entity.Material;
import com.supplychain.inventory.entity.Warehouse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class InventoryEventFactory {

    public InventoryEvent buildReceiptEvent(InventoryTransaction trx, Material material, Warehouse warehouse, String tenantId) {
        return InventoryEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(InventoryEvent.EventType.INVENTORY_RECEIVED)
                .transactionNo(trx.getTrxNo())
                .tenantId(tenantId)
                .materialId(material.getId())
                .materialCode(material.getMaterialCode())
                .materialName(material.getMaterialName())
                .destinationWarehouseId(warehouse.getId())
                .destinationWarehouseCode(warehouse.getWarehouseCode())
                .quantity(trx.getQuantity())
                .unit(material.getUnit())
                .supplierId(trx.getSupplierCode())
                .performedBy(trx.getPerformedBy())
                .occurredAt(LocalDateTime.now())
                .build();
    }

    public InventoryEvent buildTransferEvent(InventoryTransaction trx, Material material, Warehouse sourceWh, Warehouse destWh, String tenantId) {
        return InventoryEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(InventoryEvent.EventType.INVENTORY_TRANSFERRED)
                .transactionNo(trx.getTrxNo())
                .tenantId(tenantId)
                .materialId(material.getId())
                .materialCode(material.getMaterialCode())
                .materialName(material.getMaterialName())
                .sourceWarehouseId(sourceWh.getId())
                .sourceWarehouseCode(sourceWh.getWarehouseCode())
                .destinationWarehouseId(destWh.getId())
                .destinationWarehouseCode(destWh.getWarehouseCode())
                .quantity(trx.getQuantity())
                .unit(material.getUnit())
                .performedBy(trx.getPerformedBy())
                .occurredAt(LocalDateTime.now())
                .build();
    }

    public InventoryEvent buildIssueEvent(InventoryTransaction trx, Material material, Warehouse warehouse, String tenantId) {
        return InventoryEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(InventoryEvent.EventType.INVENTORY_ISSUED)
                .transactionNo(trx.getTrxNo())
                .tenantId(tenantId)
                .materialId(material.getId())
                .materialCode(material.getMaterialCode())
                .materialName(material.getMaterialName())
                .sourceWarehouseId(warehouse.getId())
                .sourceWarehouseCode(warehouse.getWarehouseCode())
                .quantity(trx.getQuantity())
                .unit(material.getUnit())
                .performedBy(trx.getPerformedBy())
                .occurredAt(LocalDateTime.now())
                .build();
    }

    public InventoryEvent buildAdjustmentEvent(InventoryTransaction trx, Material material, Warehouse warehouse, String tenantId) {
        return InventoryEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(InventoryEvent.EventType.INVENTORY_ADJUSTED)
                .transactionNo(trx.getTrxNo())
                .tenantId(tenantId)
                .materialId(material.getId())
                .materialCode(material.getMaterialCode())
                .materialName(material.getMaterialName())
                .destinationWarehouseId(warehouse.getId())
                .destinationWarehouseCode(warehouse.getWarehouseCode())
                .quantity(trx.getQuantity())
                .unit(material.getUnit())
                .performedBy(trx.getPerformedBy())
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
