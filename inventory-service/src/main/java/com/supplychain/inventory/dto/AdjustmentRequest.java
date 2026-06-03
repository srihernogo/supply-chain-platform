package com.supplychain.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentRequest {
    @NotNull(message = "Material ID is required")
    private Long materialId;

    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;

    @NotNull(message = "New quantity is required")
    @DecimalMin(value = "0.0", message = "New quantity cannot be negative")
    private BigDecimal newQuantity;

    private String referenceNo;
    private String reason;
}
