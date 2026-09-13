package com.vpf.service;

import com.vpf.dto.*;
import com.vpf.entity.Supplier;
import com.vpf.exception.ResourceNotFoundException;
import com.vpf.repository.PurchaseRepository;
import com.vpf.repository.SupplierLedgerEntryRepository;
import com.vpf.repository.SupplierPaymentRepository;
import com.vpf.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final SupplierLedgerEntryRepository supplierLedgerEntryRepository;
    private final LedgerService ledgerService;

    @Transactional
    public SupplierResponse create(SupplierRequest req) {
        Supplier s = new Supplier();
        apply(s, req);
        supplierRepository.save(s);
        ledgerService.recordSupplierOpeningBalance(s);
        return toResponse(s);
    }

    public SupplierResponse update(Long id, SupplierRequest req) {
        Supplier s = getOrThrow(id);
        apply(s, req);
        supplierRepository.save(s);
        return toResponse(s);
    }

    /** Permanently deletes a supplier and every purchase/payment/ledger entry tied to them. Admin-only. */
    @Transactional
    public void delete(Long id) {
        Supplier s = getOrThrow(id);
        supplierLedgerEntryRepository.deleteAll(supplierLedgerEntryRepository.findBySupplierIdOrderByEntryDateAscIdAsc(id));
        supplierPaymentRepository.deleteAll(supplierPaymentRepository.findBySupplierIdOrderByPaymentDateDesc(id));
        purchaseRepository.deleteAll(purchaseRepository.findBySupplierIdOrderByPurchaseDateDesc(id));
        supplierRepository.delete(s);
    }

    public List<SupplierResponse> findAll() {
        return supplierRepository.findAll().stream().map(this::toResponse).toList();
    }

    public SupplierResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    public Supplier getOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id));
    }

    public SupplierAccountSummary getAccountSummary(Long id) {
        Supplier s = getOrThrow(id);
        BigDecimal totalPurchased = purchaseRepository.sumPurchasesForSupplier(id);
        BigDecimal totalPaid = supplierPaymentRepository.sumPaidForSupplier(id);
        BigDecimal outstanding = ledgerService.getCurrentSupplierBalance(s);
        return SupplierAccountSummary.builder()
                .supplier(toResponse(s))
                .currentOutstandingPayable(outstanding)
                .totalPurchased(totalPurchased)
                .totalPaid(totalPaid)
                .build();
    }

    private void apply(Supplier s, SupplierRequest req) {
        s.setSupplierName(req.getSupplierName());
        s.setContactPerson(req.getContactPerson());
        s.setPhoneNumber(req.getPhoneNumber());
        s.setAddress(req.getAddress());
        if (s.getId() == null) {
            s.setOpeningPayableBalance(req.getOpeningPayableBalance() == null ? BigDecimal.ZERO : req.getOpeningPayableBalance());
        }
        s.setStatus(req.getStatus());
        s.setNotes(req.getNotes());
    }

    private SupplierResponse toResponse(Supplier s) {
        return SupplierResponse.builder()
                .id(s.getId())
                .supplierName(s.getSupplierName())
                .contactPerson(s.getContactPerson())
                .phoneNumber(s.getPhoneNumber())
                .address(s.getAddress())
                .openingPayableBalance(s.getOpeningPayableBalance())
                .status(s.getStatus())
                .notes(s.getNotes())
                .createdDate(s.getCreatedDate())
                .updatedDate(s.getUpdatedDate())
                .build();
    }
}
