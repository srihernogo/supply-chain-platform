package com.supplychain.inventory.outbox;

import com.supplychain.common.tenant.TenantContext;
import com.supplychain.inventory.config.TenantSchemas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules outbox polling per tenant schema and delegates publishing to {@link OutboxRelayProcessor}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMessageRelay {

    private final OutboxRelayProcessor relayProcessor;

    @Scheduled(fixedDelayString = "${inventory.outbox.relay-interval-ms:5000}")
    public void relayPendingEvents() {
        for (String tenant : TenantSchemas.ALL) {
            TenantContext.setCurrentTenant(tenant);
            try {
                relayProcessor.relayForCurrentTenant();
            } catch (Exception e) {
                log.error("Outbox relay failed for tenant={}", tenant, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
