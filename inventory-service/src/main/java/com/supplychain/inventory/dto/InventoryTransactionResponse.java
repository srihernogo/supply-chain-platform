package com.supplychain.inventory.dto;

import com.supplychain.inventory.entity.InventoryTransaction;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransactionResponse {
    private Long id;
    private String trxNo;
    private String trxType;
    private BigDecimal quantity;
    private Long materialId;
    private String materialCode;
    private Long sourceWarehouseId;
    private String sourceWarehouseCode;
    private Long destinationWarehouseId;
    private String destinationWarehouseCode;
    private String referenceNo;
    private String performedBy;
    private LocalDateTime trxTime;

    public static InventoryTransactionResponse from(InventoryTransaction trx) {
        return InventoryTransactionResponse.builder()
                .id(trx.getId())
                .trxNo(trx.getTrxNo())
                .trxType(trx.getTrxType().name())
                .quantity(trx.getQuantity())
                .materialId(trx.getMaterial().getId())
                .materialCode(trx.getMaterial().getMaterialCode())
                .sourceWarehouseId(trx.getSourceWarehouse() != null ? trx.getSourceWarehouse().getId() : null)
                .sourceWarehouseCode(trx.getSourceWarehouse() != null ? trx.getSourceWarehouse().getWarehouseCode() : null)
                .destinationWarehouseId(trx.getDestinationWarehouse() != null ? trx.getDestinationWarehouse().getId() : null)
                .destinationWarehouseCode(trx.getDestinationWarehouse() != null ? trx.getDestinationWarehouse().getWarehouseCode() : null)
                .referenceNo(trx.getReferenceNo())
                .performedBy(trx.getPerformedBy())
                .trxTime(trx.getTrxTime())
                .build();
    }
}
