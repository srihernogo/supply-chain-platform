package com.supplychain.inventory.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.common.event.InventoryEvent;
import com.supplychain.inventory.config.OutboxProperties;
import com.supplychain.inventory.entity.OutboxEvent;
import com.supplychain.inventory.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayProcessor {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, InventoryEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProperties outboxProperties;

    @Transactional
    public void relayForCurrentTenant() {
        List<OutboxEvent> pending = outboxRepository.findPendingForRelay(
                OutboxEvent.Status.PENDING,
                PageRequest.of(0, outboxProperties.getBatchSize()));

        for (OutboxEvent row : pending) {
            publishRow(row);
        }
    }

    private void publishRow(OutboxEvent row) {
        try {
            InventoryEvent event = objectMapper.readValue(row.getPayload(), InventoryEvent.class);
            kafkaTemplate.send(InventoryEvent.TOPIC, event.getTenantId(), event)
                    .get(outboxProperties.getSendTimeoutSeconds(), TimeUnit.SECONDS);
            row.markSent();
            log.info("Outbox event published: eventId={}, type={}, tenant={}",
                    row.getEventId(), row.getEventType(), row.getTenantId());
        } catch (Exception e) {
            row.recordFailure(e.getMessage(), outboxProperties.getMaxRetries());
            log.warn("Outbox publish failed: eventId={}, retry={}, error={}",
                    row.getEventId(), row.getRetryCount(), e.getMessage());
        }
        outboxRepository.save(row);
    }
}
