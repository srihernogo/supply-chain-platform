package com.supplychain.blockchain.repository;

import com.supplychain.blockchain.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findTopByOrderByBlockIndexDesc();

    Optional<Block> findByTransactionNo(String transactionNo);

    List<Block> findAllByOrderByBlockIndexAsc();
}
