package com.supplychain.supplier.service;

import com.supplychain.common.tenant.TenantContext;
import com.supplychain.supplier.dto.SupplierRequest;
import com.supplychain.supplier.dto.SupplierResponse;
import com.supplychain.supplier.entity.Supplier;
import com.supplychain.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplierService Unit Tests")
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private SupplierService supplierService;

    private Supplier testSupplier;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("toyota");
        testSupplier = Supplier.builder()
                .id(1L)
                .supplierCode("SUP-001")
                .supplierName("PT Baja Nusantara")
                .status(Supplier.SupplierStatus.ACTIVE)
                .country("Indonesia")
                .build();
    }

    @Test
    @DisplayName("createSupplier — should save and return supplier")
    void createSupplier_success() {
        SupplierRequest request = new SupplierRequest();
        request.setSupplierCode("SUP-NEW");
        request.setSupplierName("PT New Supplier");
        request.setCountry("Indonesia");

        when(supplierRepository.existsBySupplierCode("SUP-NEW")).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> {
            Supplier s = inv.getArgument(0);
            s.setId(99L);
            return s;
        });

        SupplierResponse result = supplierService.createSupplier(request, "admin@toyota");

        assertThat(result.getSupplierCode()).isEqualTo("SUP-NEW");
        assertThat(result.getSupplierName()).isEqualTo("PT New Supplier");
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    @DisplayName("createSupplier — should throw when supplier code already exists")
    void createSupplier_duplicateCode() {
        SupplierRequest request = new SupplierRequest();
        request.setSupplierCode("SUP-001");
        request.setSupplierName("Duplicate");

        when(supplierRepository.existsBySupplierCode("SUP-001")).thenReturn(true);

        assertThatThrownBy(() -> supplierService.createSupplier(request, "admin@toyota"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUP-001");
    }

    @Test
    @DisplayName("activateSupplier — should change status to ACTIVE")
    void activateSupplier_success() {
        testSupplier.setStatus(Supplier.SupplierStatus.PENDING);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(testSupplier));
        when(supplierRepository.save(any())).thenReturn(testSupplier);

        SupplierResponse result = supplierService.activateSupplier(1L);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("searchSuppliers — should return paginated results")
    void searchSuppliers_success() {
        Page<Supplier> page = new PageImpl<>(List.of(testSupplier));
        when(supplierRepository.searchSuppliers(any(), any(), any(Pageable.class))).thenReturn(page);

        Page<SupplierResponse> result = supplierService.searchSuppliers("Baja", null, Pageable.ofSize(20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSupplierCode()).isEqualTo("SUP-001");
    }
}
