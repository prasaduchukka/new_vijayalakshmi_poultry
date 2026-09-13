package com.vpf.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PurchaseRequest {
    @NotNull
    private Long supplierId;
    @NotNull
    private LocalDate purchaseDate;
    private Integer numberOfBirds;
    private Integer numberOfBoxes;
    @NotNull
    @Positive
    private BigDecimal purchaseWeight;
    /** Optional - cost tracking is no longer required on the quick entry form. */
    private BigDecimal purchaseRate;
    private BigDecimal purchaseAmount;
    private String notes;
    private String createdBy;

    /** Optional trip/vehicle info - same vehicle that later drops off at chicken centers. */
    private TripInfo trip;
}
