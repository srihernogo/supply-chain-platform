package com.supplychain.supplier.dto;

import com.supplychain.supplier.entity.Supplier;
import com.supplychain.supplier.entity.SupplierContact;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SupplierResponse {
    private Long              id;
    private String            supplierCode;
    private String            supplierName;
    private String            taxNumber;
    private String            email;
    private String            phone;
    private String            address;
    private String            city;
    private String            country;
    private String            status;
    private String            notes;
    private String            createdBy;
    private LocalDateTime     createdAt;
    private LocalDateTime     updatedAt;
    private List<ContactDTO>  contacts;

    public static SupplierResponse from(Supplier s) {
        return SupplierResponse.builder()
                .id(s.getId())
                .supplierCode(s.getSupplierCode())
                .supplierName(s.getSupplierName())
                .taxNumber(s.getTaxNumber())
                .email(s.getEmail())
                .phone(s.getPhone())
                .address(s.getAddress())
                .city(s.getCity())
                .country(s.getCountry())
                .status(s.getStatus().name())
                .notes(s.getNotes())
                .createdBy(s.getCreatedBy())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .contacts(s.getContacts().stream().map(ContactDTO::from).toList())
                .build();
    }

    @Data
    @Builder
    public static class ContactDTO {
        private Long    id;
        private String  contactName;
        private String  contactEmail;
        private String  contactPhone;
        private String  role;
        private boolean primary;

        public static ContactDTO from(SupplierContact c) {
            return ContactDTO.builder()
                    .id(c.getId())
                    .contactName(c.getContactName())
                    .contactEmail(c.getContactEmail())
                    .contactPhone(c.getContactPhone())
                    .role(c.getRole())
                    .primary(c.isPrimary())
                    .build();
        }
    }
}
