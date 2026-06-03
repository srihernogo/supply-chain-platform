package com.supplychain.inventory.event;

import com.supplychain.common.event.InventoryEvent;
import com.supplychain.inventory.entity.InventoryTransaction;
import com.supplychain.inventory.entity.Material;
import com.supplychain.inventory.entity.Warehouse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventPublisher {

    private final KafkaTemplate<String, InventoryEvent> kafkaTemplate;

    public void publishReceiptEvent(InventoryTransaction trx, Material material, Warehouse warehouse, String tenantId) {
        InventoryEvent event = InventoryEvent.builder()
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

        sendEvent(event);
    }

    public void publishTransferEvent(InventoryTransaction trx, Material material, Warehouse sourceWh, Warehouse destWh, String tenantId) {
        InventoryEvent event = InventoryEvent.builder()
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

        sendEvent(event);
    }

    public void publishIssueEvent(InventoryTransaction trx, Material material, Warehouse warehouse, String tenantId) {
        InventoryEvent event = InventoryEvent.builder()
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

        sendEvent(event);
    }

    public void publishAdjustmentEvent(InventoryTransaction trx, Material material, Warehouse warehouse, String tenantId) {
        InventoryEvent event = InventoryEvent.builder()
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

        sendEvent(event);
    }

    private void sendEvent(InventoryEvent event) {
        log.info("Publishing Kafka event: id={}, type={}, key={}", event.getEventId(), event.getEventType(), event.getTenantId());
        // Use tenantId as partition key to preserve ordering per tenant
        kafkaTemplate.send(InventoryEvent.TOPIC, event.getTenantId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event to Kafka: id={}", event.getEventId(), ex);
                    } else {
                        log.debug("Event successfully published: id={}, topic={}, partition={}, offset={}",
                                event.getEventId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
