package com.supplychain.blockchain.controller;

import com.supplychain.common.dto.ApiResponse;
import com.supplychain.blockchain.entity.Block;
import com.supplychain.blockchain.repository.BlockRepository;
import com.supplychain.blockchain.service.BlockchainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/blockchain")
@RequiredArgsConstructor
public class ChainVerificationController {

    private final BlockchainService blockchainService;
    private final BlockRepository blockRepository;

    @GetMapping("/verify")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public ResponseEntity<ApiResponse<Boolean>> verifyChain() {
        boolean isValid = blockchainService.verifyChain();
        if (isValid) {
            return ResponseEntity.ok(ApiResponse.ok("Blockchain integrity verified successfully — no tampering detected", true));
        } else {
            return ResponseEntity.ok(ApiResponse.error("WARNING: Blockchain integrity check failed — tampering detected", false));
        }
    }

    @GetMapping("/blocks")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public ResponseEntity<ApiResponse<List<Block>>> getBlocks() {
        return ResponseEntity.ok(ApiResponse.ok(blockRepository.findAllByOrderByBlockIndexAsc()));
    }
}
