package com.vpf.service;

import com.vpf.dto.PurchaseRequest;
import com.vpf.dto.PurchaseResponse;
import com.vpf.entity.Purchase;
import com.vpf.entity.Supplier;
import com.vpf.entity.Trip;
import com.vpf.entity.enums.LedgerReferenceType;
import com.vpf.exception.ResourceNotFoundException;
import com.vpf.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final SupplierService supplierService;
    private final LedgerService ledgerService;
    private final TripService tripService;

    @Transactional
    public PurchaseResponse create(PurchaseRequest req) {
        Supplier supplier = supplierService.getOrThrow(req.getSupplierId());

        Purchase p = new Purchase();
        p.setSupplier(supplier);
        p.setTrip(tripService.findOrCreate(req.getPurchaseDate(), req.getTrip()));
        p.setPurchaseDate(req.getPurchaseDate());
        p.setNumberOfBirds(req.getNumberOfBirds());
        p.setNumberOfBoxes(req.getNumberOfBoxes());
        p.setPurchaseWeight(req.getPurchaseWeight());
        p.setPurchaseRate(req.getPurchaseRate());

        // Cost tracking is optional now - only compute/store an amount if a rate was given.
        BigDecimal amount = req.getPurchaseAmount();
        if (amount == null && req.getPurchaseRate() != null) {
            amount = req.getPurchaseWeight().multiply(req.getPurchaseRate()).setScale(2, RoundingMode.HALF_UP);
        }
        p.setPurchaseAmount(amount);
        p.setNotes(req.getNotes());
        p.setCreatedBy(req.getCreatedBy());
        purchaseRepository.save(p);

        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordSupplierDebit(supplier, req.getPurchaseDate(), LedgerReferenceType.PURCHASE,
                    p.getId(), amount, "Purchase #" + p.getId() + " - " + p.getPurchaseWeight() + " kg");
        }

        return toResponse(p);
    }

    /** Admin-only edit. Re-links the trip and, if an amount is present, recalculates the ledger. */
    @Transactional
    public PurchaseResponse update(Long id, PurchaseRequest req) {
        Purchase p = purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + id));
        Supplier supplier = supplierService.getOrThrow(req.getSupplierId());
        p.setSupplier(supplier);
        p.setTrip(tripService.findOrCreate(req.getPurchaseDate(), req.getTrip()));
        p.setPurchaseDate(req.getPurchaseDate());
        p.setNumberOfBirds(req.getNumberOfBirds());
        p.setNumberOfBoxes(req.getNumberOfBoxes());
        p.setPurchaseWeight(req.getPurchaseWeight());
        p.setPurchaseRate(req.getPurchaseRate());

        BigDecimal amount = req.getPurchaseAmount();
        if (amount == null && req.getPurchaseRate() != null) {
            amount = req.getPurchaseWeight().multiply(req.getPurchaseRate()).setScale(2, RoundingMode.HALF_UP);
        }
        p.setPurchaseAmount(amount);
        p.setNotes(req.getNotes());
        purchaseRepository.save(p);

        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.replaceSupplierEntry(supplier, LedgerReferenceType.PURCHASE, p.getId(),
                    req.getPurchaseDate(), amount, null, "Purchase #" + p.getId() + " - " + p.getPurchaseWeight() + " kg");
        } else {
            // Amount was removed on edit - drop any previously-recorded ledger debit for this purchase.
            ledgerService.deleteSupplierLedgerEntryAndRecalculate(supplier, LedgerReferenceType.PURCHASE, p.getId());
        }

        return toResponse(p);
    }

    public List<PurchaseResponse> findAll() {
        return purchaseRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<PurchaseResponse> findBySupplier(Long supplierId) {
        return purchaseRepository.findBySupplierIdOrderByPurchaseDateDesc(supplierId).stream().map(this::toResponse).toList();
    }

    public List<PurchaseResponse> findByDateRange(LocalDate from, LocalDate to) {
        return purchaseRepository.findByPurchaseDateBetweenOrderByPurchaseDateAsc(from, to).stream().map(this::toResponse).toList();
    }

    public PurchaseResponse findById(Long id) {
        return toResponse(purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + id)));
    }

    /** Permanently removes a purchase and reverses its effect on the supplier's ledger. Admin-only. */
    @Transactional
    public void delete(Long id) {
        Purchase p = purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + id));
        ledgerService.deleteSupplierLedgerEntryAndRecalculate(p.getSupplier(), LedgerReferenceType.PURCHASE, id);
        purchaseRepository.delete(p);
    }

    public PurchaseResponse toResponse(Purchase p) {
        var builder = PurchaseResponse.builder()
                .id(p.getId())
                .supplierId(p.getSupplier().getId())
                .supplierName(p.getSupplier().getSupplierName())
                .purchaseDate(p.getPurchaseDate())
                .numberOfBirds(p.getNumberOfBirds())
                .numberOfBoxes(p.getNumberOfBoxes())
                .purchaseWeight(p.getPurchaseWeight())
                .purchaseRate(p.getPurchaseRate())
                .purchaseAmount(p.getPurchaseAmount())
                .notes(p.getNotes())
                .createdBy(p.getCreatedBy())
                .createdDate(p.getCreatedDate());

        if (p.getTrip() != null) {
            builder.tripId(p.getTrip().getId())
                    .vehicleNumber(p.getTrip().getVehicleNumber())
                    .companyName(p.getTrip().getCompanyName())
                    .driverName(p.getTrip().getDriverName())
                    .helperName(p.getTrip().getHelperName())
                    .totalWeightDispatched(p.getTrip().getTotalWeightDispatched())
                    .distanceKm(p.getTrip().getDistanceKm())
                    .mileage(p.getTrip().getMileage());
        }
        return builder.build();
    }
}
