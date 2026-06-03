package com.supplychain.supplier.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "supplier_contacts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "role", length = 50)
    private String role; // e.g. "Sales", "Logistics", "Finance"

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;
}
