package com.company.customer.application;

import com.company.customer.domain.Customer;
import com.company.customer.domain.PartyType;
import com.company.customer.domain.event.CustomerPartyCreated;
import com.company.customer.domain.event.CustomerPartyUpdated;
import com.company.customer.infrastructure.CustomerJpaRepository;
import com.company.platform.messaging.envelope.EventEnvelope;
import com.company.platform.messaging.outbox.OutboxEventStore;
import com.company.platform.web.correlation.CorrelationIdFilter;
import com.company.platform.web.exception.ApiException;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class CustomerApplicationService {

    private static final String CREATED_EVENT_TYPE = "customer.party.created";
    private static final String UPDATED_EVENT_TYPE = "customer.party.updated";
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

    @Transactional(readOnly = true)
    public Page<Customer> search(String query, Pageable pageable) {
        String fragment = query == null ? "" : query;
        return customerRepository.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                fragment, fragment, pageable);
    }

    /**
     * Writes the outbox row in the SAME transaction as the customer insert
     * (guide §8.4: no dual-write without outbox) — {@link OutboxEventStore#write}
     * enforces this.
     */
    @Transactional
    public Customer create(String fullName, String email, String phone, LocalDate dateOfBirth,
                            PartyType partyType) {
        Instant now = Instant.now();
        Customer customer = new Customer(UUID.randomUUID(), fullName, email, phone, dateOfBirth,
                partyType != null ? partyType : PartyType.INDIVIDUAL, now);
        customer = customerRepository.save(customer);

        var payload = new CustomerPartyCreated(customer.getId(), customer.getFullName(), customer.getEmail(),
                customer.getCreatedAt());
        publish(CREATED_EVENT_TYPE, customer.getId(), payload, customer.getCreatedAt());

        return customer;
    }

    @Transactional
    public Customer update(UUID id, String fullName, String phone, LocalDate dateOfBirth) {
        Customer customer = getById(id);
        customer.update(fullName, phone, dateOfBirth);

        var payload = new CustomerPartyUpdated(customer.getId(), customer.getFullName(), customer.getPhone(),
                customer.getDateOfBirth(), customer.getUpdatedAt());
        publish(UPDATED_EVENT_TYPE, customer.getId(), payload, customer.getUpdatedAt());

        return customer;
    }

    /**
     * The full {@link EventEnvelope} is stored as the outbox payload, not
     * just the raw domain data, so consumers see the mandatory envelope
     * shape (guide §22) on the wire once relayed.
     */
    private <T> void publish(String eventType, UUID aggregateId, T payload, Instant occurredAt) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        var envelope = new EventEnvelope<>(UUID.randomUUID(), eventType, occurredAt, correlationId, PRODUCER, 1,
                payload);
        outboxEventStore.write("Customer", aggregateId.toString(), eventType, envelope, correlationId, PRODUCER);
    }
}
