package com.vpf.service;

import com.vpf.dto.TripInfo;
import com.vpf.dto.TripResponse;
import com.vpf.entity.Trip;
import com.vpf.repository.DeliveryRepository;
import com.vpf.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * One vehicle typically visits several chicken centers in a single day, so
 * the trip-level fields (vehicle, driver, total weight, etc) are entered once
 * and reused across every delivery for that vehicle on that date - this is
 * what makes the "sticky" fields on the Record Delivery form work correctly.
 */
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final DeliveryRepository deliveryRepository;

    /** Returns null if no vehicle number was given - trip info is entirely optional. */
    public Trip findOrCreate(LocalDate date, TripInfo info) {
        if (info == null || info.getVehicleNumber() == null || info.getVehicleNumber().isBlank()) {
            return null;
        }
        Trip trip = tripRepository.findByVehicleNumberAndTripDate(info.getVehicleNumber().trim(), date)
                .orElseGet(() -> {
                    Trip t = new Trip();
                    t.setVehicleNumber(info.getVehicleNumber().trim());
                    t.setTripDate(date);
                    return t;
                });
        // Fill in/refresh any fields provided - later entries in the same day can add
        // details (like distance/mileage) that weren't known at the first stop.
        if (info.getCompanyName() != null && !info.getCompanyName().isBlank()) trip.setCompanyName(info.getCompanyName());
        if (info.getTotalWeightDispatched() != null) trip.setTotalWeightDispatched(info.getTotalWeightDispatched());
        if (info.getDriverName() != null && !info.getDriverName().isBlank()) trip.setDriverName(info.getDriverName());
        if (info.getHelperName() != null && !info.getHelperName().isBlank()) trip.setHelperName(info.getHelperName());
        if (info.getDistanceKm() != null) trip.setDistanceKm(info.getDistanceKm());
        if (info.getMileage() != null) trip.setMileage(info.getMileage());
        return tripRepository.save(trip);
    }

    public List<TripResponse> findByVehicle(String vehicleNumber) {
        return tripRepository.findByVehicleNumberOrderByTripDateDesc(vehicleNumber).stream().map(this::toResponse).toList();
    }

    public List<TripResponse> findByVehicleAndDateRange(String vehicleNumber, LocalDate from, LocalDate to) {
        return tripRepository.findByVehicleNumberAndTripDateBetweenOrderByTripDateAsc(vehicleNumber, from, to)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Powers the "Trip Details" table on the Purchases page: for each trip in the
     * date range, compares the weight loaded onto the vehicle (from the purchase
     * side) against the total actually delivered across every chicken center stop.
     */
    public List<com.vpf.dto.TripSummaryResponse> findSummaries(LocalDate from, LocalDate to) {
        List<Trip> trips = (from != null && to != null)
                ? tripRepository.findByTripDateBetweenOrderByTripDateDesc(from, to)
                : tripRepository.findAllByOrderByTripDateDesc();

        return trips.stream().map(trip -> {
            List<com.vpf.entity.Delivery> deliveries = deliveryRepository.findByTripId(trip.getId());
            java.math.BigDecimal delivered = deliveries.stream()
                    .map(com.vpf.entity.Delivery::getDispatchWeight)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal sales = deliveries.stream()
                    .map(com.vpf.entity.Delivery::getSalesAmount)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            long customerCount = deliveries.stream().map(d -> d.getCustomer().getId()).distinct().count();
            java.math.BigDecimal loaded = trip.getTotalWeightDispatched() != null ? trip.getTotalWeightDispatched() : java.math.BigDecimal.ZERO;

            return com.vpf.dto.TripSummaryResponse.builder()
                    .tripId(trip.getId())
                    .tripDate(trip.getTripDate())
                    .vehicleNumber(trip.getVehicleNumber())
                    .companyName(trip.getCompanyName())
                    .totalLoadedWeight(trip.getTotalWeightDispatched())
                    .customerCount((int) customerCount)
                    .totalDeliveredWeight(delivered)
                    .weightDifference(loaded.subtract(delivered))
                    .totalSalesAmount(sales)
                    .build();
        }).toList();
    }

    public TripResponse toResponse(Trip t) {
        return TripResponse.builder()
                .id(t.getId())
                .tripDate(t.getTripDate())
                .vehicleNumber(t.getVehicleNumber())
                .companyName(t.getCompanyName())
                .totalWeightDispatched(t.getTotalWeightDispatched())
                .driverName(t.getDriverName())
                .helperName(t.getHelperName())
                .distanceKm(t.getDistanceKm())
                .mileage(t.getMileage())
                .build();
    }
}
