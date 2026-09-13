package com.vpf.dto;

import com.vpf.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DeliveryRequest {
    @NotNull
    private Long customerId;
    private Long orderId;
    @NotNull
    private LocalDate deliveryDate;
    private Integer numberOfBoxes;
    private Integer numberOfBirds;
    @NotNull
    @Positive
    private BigDecimal dispatchWeight;
    /** Optional - when omitted, billing falls back to dispatchWeight * sellingRate. */
    @Positive
    private BigDecimal receivedWeight;
    @NotNull
    @PositiveOrZero
    private BigDecimal sellingRate;
    private String notes;
    private String createdBy;

    /** Optional trip/vehicle info - stays constant across a Save & Next batch for one vehicle's day. */
    private TripInfo trip;

    /** Optional - if the customer pays cash-on-delivery, record it in the same request. */
    private BigDecimal paymentAmount;
    private PaymentMethod paymentMethod;
    private String paymentReferenceNumber;
}
