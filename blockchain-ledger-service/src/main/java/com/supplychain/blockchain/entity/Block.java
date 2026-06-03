package com.supplychain.blockchain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "blockchain_blocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_index", nullable = false)
    private Long blockIndex;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false, length = 2000)
    private String payload;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, length = 64, unique = true)
    private String hash;

    @Column(name = "transaction_no", length = 50)
    private String transactionNo;
}
