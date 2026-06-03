package com.supplychain.inventory.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.common.event.InventoryEvent;
import com.supplychain.inventory.entity.InventoryTransaction;
import com.supplychain.inventory.entity.Material;
import com.supplychain.inventory.entity.OutboxEvent;
import com.supplychain.inventory.entity.Warehouse;
import com.supplychain.inventory.event.InventoryEventFactory;
import com.supplychain.inventory.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Transactional outbox — persists Kafka payloads in the same DB transaction as inventory writes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxRepository;
    private final InventoryEventFactory eventFactory;
    private final ObjectMapper objectMapper;

    public void enqueueReceiptEvent(InventoryTransaction trx, Material material, Warehouse warehouse, String tenantId) {
        enqueue(eventFactory.buildReceiptEvent(trx, material, warehouse, tenantId));
    }

    public void enqueueTransferEvent(InventoryTransaction trx, Material material, Warehouse sourceWh, Warehouse destWh, String tenantId) {
        enqueue(eventFactory.buildTransferEvent(trx, material, sourceWh, destWh, tenantId));
    }

    public void enqueueIssueEvent(InventoryTransaction trx, Material material, Warehouse warehouse, String tenantId) {
        enqueue(eventFactory.buildIssueEvent(trx, material, warehouse, tenantId));
    }

    public void enqueueAdjustmentEvent(InventoryTransaction trx, Material material, Warehouse warehouse, String tenantId) {
        enqueue(eventFactory.buildAdjustmentEvent(trx, material, warehouse, tenantId));
    }

    private void enqueue(InventoryEvent event) {
        try {
            OutboxEvent row = OutboxEvent.builder()
                    .eventId(event.getEventId())
                    .eventType(event.getEventType())
                    .aggregateId(event.getTransactionNo())
                    .tenantId(event.getTenantId())
                    .payload(objectMapper.writeValueAsString(event))
                    .build();
            outboxRepository.save(row);
            log.debug("Outbox event enqueued: eventId={}, type={}, trx={}",
                    event.getEventId(), event.getEventType(), event.getTransactionNo());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize inventory event for outbox", e);
        }
    }
}
