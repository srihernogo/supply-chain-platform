package com.supplychain.supplier.repository;

import com.supplychain.supplier.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findBySupplierCode(String supplierCode);

    boolean existsBySupplierCode(String supplierCode);

    Page<Supplier> findByStatus(Supplier.SupplierStatus status, Pageable pageable);

    @Query("""
        SELECT s FROM Supplier s
        WHERE (:keyword IS NULL OR
               LOWER(s.supplierName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(s.supplierCode) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:status IS NULL OR s.status = :status)
    """)
    Page<Supplier> searchSuppliers(
            @Param("keyword") String keyword,
            @Param("status") Supplier.SupplierStatus status,
            Pageable pageable
    );
}
