package com.company.customer.application;

import com.company.customer.domain.Customer;
import com.company.customer.domain.event.CustomerPartyCreated;
import com.company.customer.infrastructure.CustomerJpaRepository;
import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.messaging.outbox.OutboxEventStore;
import com.company.platform.web.correlation.CorrelationIdFilter;
import com.company.platform.web.exception.ApiException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CustomerApplicationService {

    private static final String EVENT_TYPE = "customer.party.created";
    private static final String PRODUCER = "customer-service";

    private final CustomerJpaRepository customerRepository;
    private final OutboxEventStore outboxEventStore;

    public CustomerApplicationService(CustomerJpaRepository customerRepository, OutboxEventStore outboxEventStore) {
        this.customerRepository = customerRepository;
        this.outboxEventStore = outboxEventStore;
    }

    @Transactional(readOnly = true)
    public Customer getById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CUST-4041", "Customer not found: " + id));
    }

    /**
     * Writes the outbox row in the SAME transaction as the customer insert
     * (guide §8.4: no dual-write without outbox) — {@link OutboxEventStore#write}
     * enforces this. The full {@link EventEnvelope} is stored as the payload,
     * not just the raw domain data, so consumers see the mandatory envelope
     * shape (guide §22) on the wire once relayed.
     */
    @Transactional
    public Customer create(String fullName, String email) {
        Customer customer = new Customer(UUID.randomUUID(), fullName, email, Instant.now());
        customer = customerRepository.save(customer);

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        var payload = new CustomerPartyCreated(customer.getId(), customer.getFullName(), customer.getEmail(),
                customer.getCreatedAt());
        var envelope = new EventEnvelope<>(UUID.randomUUID(), EVENT_TYPE, customer.getCreatedAt(), correlationId,
                PRODUCER, 1, payload);
        outboxEventStore.write("Customer", customer.getId().toString(), EVENT_TYPE, envelope, correlationId,
                PRODUCER);

        return customer;
    }
}
