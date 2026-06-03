package com.supplychain.blockchain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@EnableJpaAuditing
@SpringBootApplication
public class BlockchainLedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlockchainLedgerApplication.class, args);
    }
}
