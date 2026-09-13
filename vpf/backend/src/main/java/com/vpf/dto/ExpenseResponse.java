package com.vpf.dto;

import com.vpf.entity.enums.ExpenseCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ExpenseResponse {
    private Long id;
    private LocalDate expenseDate;
    private ExpenseCategory category;
    private BigDecimal amount;
    private String description;
    private String notes;
    private String createdBy;
    private LocalDateTime createdDate;
}
