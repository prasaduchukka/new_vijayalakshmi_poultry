package com.vpf.dto;

import com.vpf.entity.enums.ExpenseCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseRequest {
    @NotNull
    private LocalDate expenseDate;
    @NotNull
    private ExpenseCategory category;
    @NotNull
    @Positive
    private BigDecimal amount;
    private String description;
    private String notes;
    private String createdBy;
}
