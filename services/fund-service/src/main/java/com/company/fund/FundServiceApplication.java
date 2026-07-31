package com.company.fund;

import com.company.fund.domain.Fund;
import com.company.fund.domain.NavSnapshot;
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
@EntityScan(basePackageClasses = {Fund.class, NavSnapshot.class, OutboxEvent.class, ProcessedEvent.class})
public class FundServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundServiceApplication.class, args);
    }
}
