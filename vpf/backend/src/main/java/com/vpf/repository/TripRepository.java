package com.vpf.repository;

import com.vpf.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {
    Optional<Trip> findByVehicleNumberAndTripDate(String vehicleNumber, LocalDate tripDate);
    List<Trip> findByVehicleNumberOrderByTripDateDesc(String vehicleNumber);
    List<Trip> findByVehicleNumberAndTripDateBetweenOrderByTripDateAsc(String vehicleNumber, LocalDate from, LocalDate to);
    List<Trip> findAllByOrderByTripDateDesc();
    List<Trip> findByTripDateBetweenOrderByTripDateDesc(LocalDate from, LocalDate to);
}
