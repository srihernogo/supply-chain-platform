package com.supplychain.supplier.service;

import com.supplychain.common.exception.ResourceNotFoundException;
import com.supplychain.supplier.dto.SupplierRequest;
import com.supplychain.supplier.dto.SupplierResponse;
import com.supplychain.supplier.entity.Supplier;
import com.supplychain.supplier.entity.SupplierContact;
import com.supplychain.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    // ─── Create ───────────────────────────────────────────

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request, String createdBy) {
        if (supplierRepository.existsBySupplierCode(request.getSupplierCode())) {
            throw new IllegalArgumentException("Supplier code already exists: " + request.getSupplierCode());
        }

        Supplier supplier = Supplier.builder()
                .supplierCode(request.getSupplierCode())
                .supplierName(request.getSupplierName())
                .taxNumber(request.getTaxNumber())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry())
                .status(Supplier.SupplierStatus.PENDING)
                .notes(request.getNotes())
                .createdBy(createdBy)
                .build();

        // Add contacts if provided
        if (request.getContacts() != null) {
            request.getContacts().forEach(c -> {
                SupplierContact contact = SupplierContact.builder()
                        .supplier(supplier)
                        .contactName(c.getContactName())
                        .contactEmail(c.getContactEmail())
                        .contactPhone(c.getContactPhone())
                        .role(c.getRole())
                        .primary(c.isPrimary())
                        .build();
                supplier.getContacts().add(contact);
            });
        }

        Supplier saved = supplierRepository.save(supplier);
        log.info("Supplier created: code={}, name={}, by={}", saved.getSupplierCode(), saved.getSupplierName(), createdBy);
        return SupplierResponse.from(saved);
    }

    // ─── Read ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Long id) {
        return supplierRepository.findById(id)
                .map(SupplierResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierByCode(String code) {
        return supplierRepository.findBySupplierCode(code)
                .map(SupplierResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with code: " + code));
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> searchSuppliers(String keyword, String status, Pageable pageable) {
        Supplier.SupplierStatus supplierStatus = null;
        if (status != null && !status.isBlank()) {
            supplierStatus = Supplier.SupplierStatus.valueOf(status.toUpperCase());
        }
        return supplierRepository.searchSuppliers(keyword, supplierStatus, pageable)
                .map(SupplierResponse::from);
    }

    // ─── Update ───────────────────────────────────────────

    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));

        supplier.setSupplierName(request.getSupplierName());
        supplier.setTaxNumber(request.getTaxNumber());
        supplier.setEmail(request.getEmail());
        supplier.setPhone(request.getPhone());
        supplier.setAddress(request.getAddress());
        supplier.setCity(request.getCity());
        supplier.setCountry(request.getCountry());
        supplier.setNotes(request.getNotes());

        log.info("Supplier updated: id={}, code={}", id, supplier.getSupplierCode());
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    // ─── Status Management ────────────────────────────────

    @Transactional
    public SupplierResponse activateSupplier(Long id) {
        return changeStatus(id, Supplier.SupplierStatus.ACTIVE);
    }

    @Transactional
    public SupplierResponse suspendSupplier(Long id) {
        return changeStatus(id, Supplier.SupplierStatus.SUSPENDED);
    }

    @Transactional
    public SupplierResponse blacklistSupplier(Long id) {
        return changeStatus(id, Supplier.SupplierStatus.BLACKLISTED);
    }

    private SupplierResponse changeStatus(Long id, Supplier.SupplierStatus newStatus) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
        supplier.setStatus(newStatus);
        log.info("Supplier status changed: id={}, newStatus={}", id, newStatus);
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    // ─── Delete ───────────────────────────────────────────

    @Transactional
    public void deleteSupplier(Long id) {
        if (!supplierRepository.existsById(id)) {
            throw new ResourceNotFoundException("Supplier", id);
        }
        supplierRepository.deleteById(id);
        log.info("Supplier deleted: id={}", id);
    }

    // ─── Validation (used by Inventory Service) ────────────

    @Transactional(readOnly = true)
    public List<SupplierResponse> getActiveSuppliers() {
        return supplierRepository
                .findByStatus(Supplier.SupplierStatus.ACTIVE, Pageable.unpaged())
                .map(SupplierResponse::from)
                .toList();
    }
}
