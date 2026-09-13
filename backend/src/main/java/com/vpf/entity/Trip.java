package com.vpf.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One vehicle's round trip on a given day: picks up from a supplier (Purchase)
 * and drops off at one or more chicken centers (Deliveries). Recording the
 * same trip info on both sides lets the owner cross-check the supplier's
 * dispatch weight against what actually got delivered.
 */
@Getter
@Setter
@Entity
@Table(name = "trip")
public class Trip extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate tripDate;

    @Column(nullable = false)
    private String vehicleNumber;

    private String companyName;

    /** Total weight loaded onto the vehicle for the whole trip, before splitting across stops. */
    @Column(precision = 10, scale = 2)
    private BigDecimal totalWeightDispatched;

    private String driverName;

    private String helperName;

    @Column(precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(precision = 10, scale = 2)
    private BigDecimal mileage;

    @Column(length = 1000)
    private String notes;
}
