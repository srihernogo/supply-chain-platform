package com.supplychain.blockchain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.common.event.InventoryEvent;
import com.supplychain.common.tenant.TenantContext;
import com.supplychain.blockchain.service.BlockchainService;
import com.supplychain.blockchain.entity.Block;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final BlockchainService blockchainService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = InventoryEvent.TOPIC, groupId = "blockchain-ledger-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(InventoryEvent event) {
        log.info("Received InventoryEvent: id={}, type={}, tenant={}, transaction={}",
                event.getEventId(), event.getEventType(), event.getTenantId(), event.getTransactionNo());

        if (event.getTenantId() == null) {
            log.error("Rejecting event: tenantId is missing");
            return;
        }

        try {
            // Set multi-tenant context for the consumer thread
            TenantContext.setCurrentTenant(event.getTenantId());

            // Convert event payload to JSON string for block storage
            String payloadJson = objectMapper.writeValueAsString(event);

            // Use idempotent append: skip if eventId already processed
            Block result = blockchainService.addBlockIfNotProcessed(event.getEventId(), event.getTransactionNo(),
                    payloadJson);
            if (result == null) {
                log.info("Skipping event because it was already processed: eventId={}", event.getEventId());
            } else {
                log.info("Event successfully registered to blockchain: eventId={}", event.getEventId());
            }
        } catch (Exception e) {
            log.error("Failed to register event to blockchain: eventId={}", event.getEventId(), e);
            // In a production app, we would throw to trigger DLQ processing
            throw new RuntimeException("Error writing block, triggering Kafka retry/DLQ", e);
        } finally {
            TenantContext.clear();
        }
    }
}
