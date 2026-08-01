package com.company.reporting;

import com.company.reporting.domain.FundNavView;
import com.company.reporting.domain.PaymentTransferView;
import com.company.reporting.domain.PortfolioView;
import com.company.reporting.domain.PositionView;
import com.company.platform.messaging.idempotent.ProcessedEvent;
import com.company.platform.messaging.outbox.OutboxEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * {@code @EntityScan} must list this service's own domain package alongside
 * common-messaging's, or its own entities silently stop being managed types
 * (see common-messaging's README, and Audit Service's own application class
 * for the same note).
 */
@SpringBootApplication
@EntityScan(basePackageClasses = {FundNavView.class, PortfolioView.class, PositionView.class,
        PaymentTransferView.class, OutboxEvent.class, ProcessedEvent.class})
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
