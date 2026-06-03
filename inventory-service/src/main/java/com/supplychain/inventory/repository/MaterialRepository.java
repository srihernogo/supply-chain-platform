package com.supplychain.inventory.repository;

import com.supplychain.inventory.entity.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {

    Optional<Material> findByMaterialCode(String materialCode);

    boolean existsByMaterialCode(String materialCode);

    @Query("""
        SELECT m FROM Material m
        WHERE (:keyword IS NULL OR
               LOWER(m.materialName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(m.materialCode) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:category IS NULL OR m.category = :category)
        AND m.active = true
    """)
    Page<Material> search(@Param("keyword") String keyword,
                          @Param("category") String category,
                          Pageable pageable);
}
