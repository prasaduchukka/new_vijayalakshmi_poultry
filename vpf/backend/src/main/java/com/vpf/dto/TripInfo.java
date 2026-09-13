package com.vpf.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Embedded in DeliveryRequest/PurchaseRequest. All fields optional - if
 * vehicleNumber is provided, the backend finds-or-creates a Trip for that
 * vehicle+date and links this delivery/purchase to it.
 */
@Data
public class TripInfo {
    private String vehicleNumber;
    private String companyName;
    private BigDecimal totalWeightDispatched;
    private String driverName;
    private String helperName;
    private BigDecimal distanceKm;
    private BigDecimal mileage;
}
