package com.company.aml;

import com.company.aml.domain.AmlScreening;
import com.company.platform.messaging.idempotent.ProcessedEvent;
import com.company.platform.messaging.outbox.OutboxEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * {@code @EntityScan} is required here, not optional — see
 * common-messaging's README: the moment ANY {@code @EntityScan} exists in
 * the context, Spring Boot's implicit "scan my own package" default turns
 * off for the WHOLE application, not just for that library.
 */
@SpringBootApplication
@EntityScan(basePackageClasses = {AmlScreening.class, OutboxEvent.class, ProcessedEvent.class})
public class AmlServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmlServiceApplication.class, args);
    }
}
