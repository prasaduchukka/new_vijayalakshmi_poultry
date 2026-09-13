package com.vpf.service;

import com.vpf.dto.DeliveryRequest;
import com.vpf.dto.DeliveryResponse;
import com.vpf.entity.Customer;
import com.vpf.entity.CustomerOrder;
import com.vpf.entity.Delivery;
import com.vpf.entity.Trip;
import com.vpf.entity.enums.LedgerReferenceType;
import com.vpf.entity.enums.OrderStatus;
import com.vpf.exception.BusinessRuleException;
import com.vpf.exception.ResourceNotFoundException;
import com.vpf.repository.CustomerOrderRepository;
import com.vpf.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Implements the confirmed billing rule, with a fallback for the simplified
 * current workflow:
 *   - If Received Weight is provided: Weight Difference = Dispatch - Received (KG only),
 *     and Sales Amount = Received Weight * Selling Rate (original confirmed rule).
 *   - If Received Weight is NOT provided (current default entry flow): Sales Amount =
 *     Dispatch Weight * Selling Rate, and Weight Difference is left blank.
 * A delivery can optionally carry vehicle/trip info (one vehicle visiting several
 * chicken centers in a day) and an optional cash-on-delivery payment recorded
 * in the same request.
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final CustomerOrderRepository orderRepository;
    private final CustomerService customerService;
    private final LedgerService ledgerService;
    private final TripService tripService;
    private final CustomerPaymentService customerPaymentService;

    @Transactional
    public DeliveryResponse create(DeliveryRequest req) {
        Customer customer = customerService.getOrThrow(req.getCustomerId());

        if (req.getReceivedWeight() != null && req.getReceivedWeight().compareTo(req.getDispatchWeight()) > 0) {
            throw new BusinessRuleException("Received weight cannot be greater than dispatch weight.");
        }

        Delivery d = new Delivery();
        d.setCustomer(customer);

        if (req.getOrderId() != null) {
            CustomerOrder order = orderRepository.findById(req.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + req.getOrderId()));
            d.setOrder(order);
            if (order.getStatus() != OrderStatus.CANCELLED) {
                order.setStatus(OrderStatus.DELIVERED);
                orderRepository.save(order);
            }
        }

        Trip trip = tripService.findOrCreate(req.getDeliveryDate(), req.getTrip());
        d.setTrip(trip);

        d.setDeliveryDate(req.getDeliveryDate());
        d.setNumberOfBoxes(req.getNumberOfBoxes());
        d.setNumberOfBirds(req.getNumberOfBirds());
        d.setDispatchWeight(req.getDispatchWeight());
        d.setReceivedWeight(req.getReceivedWeight());
        d.setSellingRate(req.getSellingRate());

        BigDecimal billingWeight;
        if (req.getReceivedWeight() != null) {
            d.setWeightDifference(req.getDispatchWeight().subtract(req.getReceivedWeight()).setScale(2, RoundingMode.HALF_UP));
            billingWeight = req.getReceivedWeight();
        } else {
            d.setWeightDifference(null);
            billingWeight = req.getDispatchWeight();
        }

        d.setSalesAmount(billingWeight.multiply(req.getSellingRate()).setScale(2, RoundingMode.HALF_UP));

        d.setNotes(req.getNotes());
        d.setCreatedBy(req.getCreatedBy());
        deliveryRepository.save(d);

        ledgerService.recordCustomerDebit(customer, req.getDeliveryDate(), LedgerReferenceType.DELIVERY,
                d.getId(), d.getSalesAmount(),
                "Delivery #" + d.getId() + " @ Rs." + d.getSellingRate() + "/kg");

        if (req.getPaymentAmount() != null && req.getPaymentAmount().compareTo(BigDecimal.ZERO) > 0) {
            com.vpf.dto.CustomerPaymentRequest payReq = new com.vpf.dto.CustomerPaymentRequest();
            payReq.setCustomerId(customer.getId());
            payReq.setPaymentDate(req.getDeliveryDate());
            payReq.setAmount(req.getPaymentAmount());
            payReq.setPaymentMethod(req.getPaymentMethod() != null ? req.getPaymentMethod() : com.vpf.entity.enums.PaymentMethod.CASH);
            payReq.setReferenceNumber(req.getPaymentReferenceNumber());
            payReq.setNotes("Paid at delivery #" + d.getId());
            payReq.setCreatedBy(req.getCreatedBy());
            customerPaymentService.create(payReq);
        }

        return toResponse(d);
    }

    /** Admin-only edit. Re-links the trip and fully recalculates the ledger from this entry forward. */
    @Transactional
    public DeliveryResponse update(Long id, DeliveryRequest req) {
        Delivery d = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + id));

        if (req.getReceivedWeight() != null && req.getReceivedWeight().compareTo(req.getDispatchWeight()) > 0) {
            throw new BusinessRuleException("Received weight cannot be greater than dispatch weight.");
        }

        Customer customer = customerService.getOrThrow(req.getCustomerId());
        d.setCustomer(customer);

        Trip trip = tripService.findOrCreate(req.getDeliveryDate(), req.getTrip());
        d.setTrip(trip);

        d.setDeliveryDate(req.getDeliveryDate());
        d.setNumberOfBoxes(req.getNumberOfBoxes());
        d.setNumberOfBirds(req.getNumberOfBirds());
        d.setDispatchWeight(req.getDispatchWeight());
        d.setReceivedWeight(req.getReceivedWeight());
        d.setSellingRate(req.getSellingRate());

        BigDecimal billingWeight;
        if (req.getReceivedWeight() != null) {
            d.setWeightDifference(req.getDispatchWeight().subtract(req.getReceivedWeight()).setScale(2, RoundingMode.HALF_UP));
            billingWeight = req.getReceivedWeight();
        } else {
            d.setWeightDifference(null);
            billingWeight = req.getDispatchWeight();
        }
        d.setSalesAmount(billingWeight.multiply(req.getSellingRate()).setScale(2, RoundingMode.HALF_UP));
        d.setNotes(req.getNotes());
        deliveryRepository.save(d);

        ledgerService.replaceCustomerEntry(customer, LedgerReferenceType.DELIVERY, d.getId(),
                req.getDeliveryDate(), d.getSalesAmount(), null,
                "Delivery #" + d.getId() + " @ Rs." + d.getSellingRate() + "/kg");

        return toResponse(d);
    }

    public List<DeliveryResponse> findAll() {
        return deliveryRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<DeliveryResponse> findByCustomer(Long customerId) {
        return deliveryRepository.findByCustomerIdOrderByDeliveryDateDesc(customerId).stream().map(this::toResponse).toList();
    }

    public List<DeliveryResponse> findByDateRange(LocalDate from, LocalDate to) {
        return deliveryRepository.findByDeliveryDateBetweenOrderByDeliveryDateAsc(from, to).stream().map(this::toResponse).toList();
    }

    public List<DeliveryResponse> findByDate(LocalDate date) {
        return deliveryRepository.findByDeliveryDate(date).stream().map(this::toResponse).toList();
    }

    public DeliveryResponse findById(Long id) {
        return toResponse(deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + id)));
    }

    /** Permanently removes a delivery and reverses its effect on the customer's ledger. Admin-only. */
    @Transactional
    public void delete(Long id) {
        Delivery d = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + id));
        ledgerService.deleteCustomerLedgerEntryAndRecalculate(d.getCustomer(), LedgerReferenceType.DELIVERY, id);
        deliveryRepository.delete(d);
    }

    public DeliveryResponse toResponse(Delivery d) {
        var builder = DeliveryResponse.builder()
                .id(d.getId())
                .customerId(d.getCustomer().getId())
                .customerName(d.getCustomer().getChickenCenterName())
                .orderId(d.getOrder() != null ? d.getOrder().getId() : null)
                .deliveryDate(d.getDeliveryDate())
                .numberOfBoxes(d.getNumberOfBoxes())
                .numberOfBirds(d.getNumberOfBirds())
                .dispatchWeight(d.getDispatchWeight())
                .receivedWeight(d.getReceivedWeight())
                .weightDifference(d.getWeightDifference())
                .sellingRate(d.getSellingRate())
                .salesAmount(d.getSalesAmount())
                .notes(d.getNotes())
                .createdBy(d.getCreatedBy())
                .createdDate(d.getCreatedDate());

        if (d.getTrip() != null) {
            builder.tripId(d.getTrip().getId())
                    .vehicleNumber(d.getTrip().getVehicleNumber())
                    .companyName(d.getTrip().getCompanyName())
                    .driverName(d.getTrip().getDriverName())
                    .helperName(d.getTrip().getHelperName())
                    .totalWeightDispatched(d.getTrip().getTotalWeightDispatched())
                    .distanceKm(d.getTrip().getDistanceKm())
                    .mileage(d.getTrip().getMileage());
        }

        return builder.build();
    }
}
