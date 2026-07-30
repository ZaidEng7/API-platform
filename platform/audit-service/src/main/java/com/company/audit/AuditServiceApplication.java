package com.company.audit;

import com.company.audit.domain.AuditEvent;
import com.company.platform.messaging.idempotent.ProcessedEvent;
import com.company.platform.messaging.outbox.OutboxEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * {@code @EntityScan} is required here, not optional: the moment ANY
 * {@code @EntityScan} exists in the context — including common-messaging's
 * own, which only lists its own entity packages — Spring Boot's implicit
 * "scan my own package" default turns off for the WHOLE application, not
 * just for that library. Every service depending on common-messaging must
 * list its own domain package alongside common-messaging's here, or its
 * own entities silently stop being managed types (see common-messaging's
 * README).
 */
@SpringBootApplication
@EntityScan(basePackageClasses = {AuditEvent.class, OutboxEvent.class, ProcessedEvent.class})
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
