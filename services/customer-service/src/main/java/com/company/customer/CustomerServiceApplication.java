package com.company.customer;

import com.company.customer.domain.Customer;
import com.company.platform.messaging.idempotent.ProcessedEvent;
import com.company.platform.messaging.outbox.OutboxEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * {@code @EntityScan} is required here, not optional: the moment ANY
 * {@code @EntityScan} exists in the context — including common-messaging's
 * own, which only lists its own entity packages — Spring Boot's implicit
 * "scan my own package" default turns off for the WHOLE application, not
 * just for that library. See common-messaging's README.
 */
@SpringBootApplication
@EntityScan(basePackageClasses = {Customer.class, OutboxEvent.class, ProcessedEvent.class})
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
