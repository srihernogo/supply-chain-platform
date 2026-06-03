package com.supplychain.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SupplierRequest {

    @NotBlank(message = "Supplier code is required")
    @Size(max = 50)
    private String supplierCode;

    @NotBlank(message = "Supplier name is required")
    @Size(max = 200)
    private String supplierName;

    @Size(max = 50)
    private String taxNumber;

    @Email(message = "Email must be valid")
    @Size(max = 100)
    private String email;

    @Size(max = 20)
    private String phone;

    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String country;

    private String notes;

    private List<ContactRequest> contacts;

    @Data
    public static class ContactRequest {
        @NotBlank
        private String contactName;
        private String contactEmail;
        private String contactPhone;
        private String role;
        private boolean primary;
    }
}
