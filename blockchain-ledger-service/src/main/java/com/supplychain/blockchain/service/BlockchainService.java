package com.supplychain.blockchain.service;

import com.supplychain.blockchain.entity.Block;
import com.supplychain.blockchain.repository.BlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainService {

    private final BlockRepository blockRepository;

    /**
     * Appends a new transaction block to the ledger.
     * Thread-safe / Transactional to prevent race conditions when appending.
     */
    @Transactional
    public synchronized Block addBlock(String transactionNo, String payload) {
        log.info("Requesting to append block: transactionNo={}", transactionNo);

        Optional<Block> lastBlockOpt = blockRepository.findTopByOrderByBlockIndexDesc();
        Block lastBlock;

        if (lastBlockOpt.isEmpty()) {
            // Create Genesis Block if chain is empty
            lastBlock = createGenesisBlock();
        } else {
            lastBlock = lastBlockOpt.get();
        }

        long nextIndex = lastBlock.getBlockIndex() + 1;
        LocalDateTime now = LocalDateTime.now();
        String prevHash = lastBlock.getHash();
        String hash = calculateHash(nextIndex, prevHash, payload, now, transactionNo);

        Block nextBlock = Block.builder()
                .blockIndex(nextIndex)
                .previousHash(prevHash)
                .hash(hash)
                .payload(payload)
                .transactionNo(transactionNo)
                .timestamp(now)
                .build();

        Block saved = blockRepository.save(nextBlock);
        log.info("Block successfully appended: index={}, hash={}", saved.getBlockIndex(), saved.getHash());
        return saved;
    }

    /**
     * Verifies the cryptographic integrity of the entire chain.
     */
    @Transactional(readOnly = true)
    public boolean verifyChain() {
        List<Block> blocks = blockRepository.findAllByOrderByBlockIndexAsc();
        if (blocks.isEmpty()) {
            log.info("Chain verification: chain is empty (valid)");
            return true;
        }

        // 1. Verify Genesis Block
        Block genesis = blocks.get(0);
        if (genesis.getBlockIndex() != 0) {
            log.error("Verification failed: genesis block index is not 0, found {}", genesis.getBlockIndex());
            return false;
        }
        if (!genesis.getPreviousHash().equals("0")) {
            log.error("Verification failed: genesis previous hash is not '0'");
            return false;
        }
        String calculatedGenesisHash = calculateHash(0L, "0", genesis.getPayload(), genesis.getTimestamp(), genesis.getTransactionNo());
        if (!genesis.getHash().equals(calculatedGenesisHash)) {
            log.error("Verification failed: genesis hash mismatch");
            return false;
        }

        // 2. Verify downstream blocks
        for (int i = 1; i < blocks.size(); i++) {
            Block current = blocks.get(i);
            Block previous = blocks.get(i - 1);

            // Verify height linkage
            if (current.getBlockIndex() != previous.getBlockIndex() + 1) {
                log.error("Verification failed: index gap between block {} and {}", previous.getBlockIndex(), current.getBlockIndex());
                return false;
            }

            // Verify previous hash linkage
            if (!current.getPreviousHash().equals(previous.getHash())) {
                log.error("Verification failed: hash linkage broken at block index {}", current.getBlockIndex());
                return false;
            }

            // Recalculate and verify hash
            String computedHash = calculateHash(
                    current.getBlockIndex(),
                    current.getPreviousHash(),
                    current.getPayload(),
                    current.getTimestamp(),
                    current.getTransactionNo()
            );

            if (!current.getHash().equals(computedHash)) {
                log.error("Verification failed: data tampering detected at block index {}", current.getBlockIndex());
                return false;
            }
        }

        log.info("Chain verification passed. Inspected {} blocks.", blocks.size());
        return true;
    }

    private Block createGenesisBlock() {
        log.info("Initializing chain with Genesis Block");
        LocalDateTime genesisTime = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        String payload = "Genesis Block - Supply Chain Platform Ledger Initialization";
        String hash = calculateHash(0L, "0", payload, genesisTime, "GENESIS");

        Block genesis = Block.builder()
                .blockIndex(0L)
                .previousHash("0")
                .hash(hash)
                .payload(payload)
                .transactionNo("GENESIS")
                .timestamp(genesisTime)
                .build();

        return blockRepository.save(genesis);
    }

    private String calculateHash(long index, String previousHash, String payload, LocalDateTime timestamp, String transactionNo) {
        String dataToHash = index + previousHash + payload + timestamp.toString() + (transactionNo != null ? transactionNo : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            throw new RuntimeException("Cryptographic error occurred during hashing", e);
        }
    }
}
