package com.vpf.service;

import com.vpf.dto.CustomerPaymentRequest;
import com.vpf.dto.CustomerPaymentResponse;
import com.vpf.entity.Customer;
import com.vpf.entity.CustomerPayment;
import com.vpf.entity.enums.LedgerReferenceType;
import com.vpf.repository.CustomerPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerPaymentService {

    private final CustomerPaymentRepository customerPaymentRepository;
    private final CustomerService customerService;
    private final LedgerService ledgerService;

    @Transactional
    public CustomerPaymentResponse create(CustomerPaymentRequest req) {
        Customer customer = customerService.getOrThrow(req.getCustomerId());

        CustomerPayment payment = new CustomerPayment();
        payment.setCustomer(customer);
        payment.setPaymentDate(req.getPaymentDate());
        payment.setAmount(req.getAmount());
        payment.setPaymentMethod(req.getPaymentMethod());
        payment.setReferenceNumber(req.getReferenceNumber());
        payment.setNotes(req.getNotes());
        payment.setCreatedBy(req.getCreatedBy());
        customerPaymentRepository.save(payment);

        // Every payment is stored as its own transaction - never overwriting past payments.
        ledgerService.recordCustomerCredit(customer, req.getPaymentDate(), LedgerReferenceType.PAYMENT,
                payment.getId(), req.getAmount(),
                "Payment received (" + req.getPaymentMethod() + ")" + (req.getReferenceNumber() != null ? " Ref: " + req.getReferenceNumber() : ""));

        return toResponse(payment);
    }

    /** Admin-only edit. Recalculates the customer's ledger from this entry forward. */
    @Transactional
    public CustomerPaymentResponse update(Long id, CustomerPaymentRequest req) {
        CustomerPayment payment = customerPaymentRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Payment not found: " + id));
        Customer customer = customerService.getOrThrow(req.getCustomerId());
        payment.setCustomer(customer);
        payment.setPaymentDate(req.getPaymentDate());
        payment.setAmount(req.getAmount());
        payment.setPaymentMethod(req.getPaymentMethod());
        payment.setReferenceNumber(req.getReferenceNumber());
        payment.setNotes(req.getNotes());
        customerPaymentRepository.save(payment);

        ledgerService.replaceCustomerEntry(customer, LedgerReferenceType.PAYMENT, payment.getId(),
                req.getPaymentDate(), null, req.getAmount(),
                "Payment received (" + req.getPaymentMethod() + ")" + (req.getReferenceNumber() != null ? " Ref: " + req.getReferenceNumber() : ""));

        return toResponse(payment);
    }

    public List<CustomerPaymentResponse> findByCustomer(Long customerId) {
        return customerPaymentRepository.findByCustomerIdOrderByPaymentDateDesc(customerId).stream().map(this::toResponse).toList();
    }

    public List<CustomerPaymentResponse> findByDateRange(LocalDate from, LocalDate to) {
        return customerPaymentRepository.findByPaymentDateBetween(from, to).stream().map(this::toResponse).toList();
    }

    /** Permanently removes a payment and reverses its effect on the customer's ledger. Admin-only. */
    @Transactional
    public void delete(Long id) {
        CustomerPayment p = customerPaymentRepository.findById(id)
                .orElseThrow(() -> new com.vpf.exception.ResourceNotFoundException("Payment not found: " + id));
        ledgerService.deleteCustomerLedgerEntryAndRecalculate(p.getCustomer(), LedgerReferenceType.PAYMENT, id);
        customerPaymentRepository.delete(p);
    }

    public CustomerPaymentResponse toResponse(CustomerPayment p) {
        return CustomerPaymentResponse.builder()
                .id(p.getId())
                .customerId(p.getCustomer().getId())
                .customerName(p.getCustomer().getChickenCenterName())
                .paymentDate(p.getPaymentDate())
                .amount(p.getAmount())
                .paymentMethod(p.getPaymentMethod())
                .referenceNumber(p.getReferenceNumber())
                .notes(p.getNotes())
                .createdBy(p.getCreatedBy())
                .createdDate(p.getCreatedDate())
                .build();
    }
}
