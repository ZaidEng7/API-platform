package com.company.investment;

import com.company.investment.domain.Subscription;
import com.company.platform.messaging.idempotent.ProcessedEvent;
import com.company.platform.messaging.outbox.OutboxEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EntityScan} is required here, not optional — see
 * common-messaging's README: the moment ANY {@code @EntityScan} exists in
 * the context, Spring Boot's implicit "scan my own package" default turns
 * off for the WHOLE application, not just for that library.
 * {@code @EnableScheduling} drives the subscription timeout job (guide
 * §8.4) — common-messaging's own autoconfiguration already enables it for
 * the outbox relay, but this service's own {@code @Scheduled} bean needs
 * it declared here too since it's outside that library.
 */
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackageClasses = {Subscription.class, OutboxEvent.class, ProcessedEvent.class})
public class InvestmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvestmentServiceApplication.class, args);
    }
}
