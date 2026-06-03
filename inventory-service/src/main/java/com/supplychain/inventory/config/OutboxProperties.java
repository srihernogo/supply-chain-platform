package com.supplychain.inventory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "inventory.outbox")
public class OutboxProperties {

    /** Delay between relay runs (ms). */
    private long relayIntervalMs = 5_000;

    /** Max outbox rows processed per tenant per run. */
    private int batchSize = 50;

    /** Mark FAILED after this many failed publish attempts. */
    private int maxRetries = 5;

    /** Kafka send timeout (seconds) during relay. */
    private int sendTimeoutSeconds = 10;
}
