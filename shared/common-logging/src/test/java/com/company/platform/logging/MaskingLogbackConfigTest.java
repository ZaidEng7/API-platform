package com.company.platform.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import net.logstash.logback.argument.StructuredArguments;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the actual logback-spring.xml this module ships and asserts the
 * masking decorator does what guide §14 requires — this is the enforcement
 * mechanism, so a regression here is a real leak, not just a broken test.
 */
class MaskingLogbackConfigTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;
    private LoggerContext context;

    @BeforeEach
    void setUp() throws Exception {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

        // Reuse the SLF4J-bound singleton context (not `new LoggerContext()`) —
        // a freshly instantiated one isn't wired to the static MDC adapter and
        // NPEs on any log call. Spring Boot's LoggingSystem reconfigures this
        // same singleton at runtime, so this matches real usage.
        context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);

        URL configUrl = getClass().getClassLoader().getResource("logback-spring.xml");
        assertThat(configUrl).as("logback-spring.xml must be on the test classpath").isNotNull();
        configurator.doConfigure(configUrl);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        context.reset();
    }

    @Test
    void masksNamedSensitiveFields() {
        // StructuredArguments.value() (not .keyValue()/kv()) adds the field to
        // the JSON output only — it does NOT inline it into the human-readable
        // message text, which is what lets path masking fully suppress it.
        Logger logger = context.getLogger("com.company.test");
        logger.info("login attempt", StructuredArguments.value("password", "s3cr3t-value"));

        String output = captured.toString(StandardCharsets.UTF_8);

        assertThat(output).doesNotContain("s3cr3t-value");
        assertThat(output).contains("***MASKED***");
    }

    @Test
    void masksIbanShapedValuesInFreeTextMessages() {
        Logger logger = context.getLogger("com.company.test");
        logger.info("payment settled for account SA0380000000608010167519");

        String output = captured.toString(StandardCharsets.UTF_8);

        assertThat(output).contains("payment settled").doesNotContain("SA0380000000608010167519");
    }

    @Test
    void includesCorrelationIdFromMdc() {
        MDC.put("correlationId", "corr-123");
        try {
            Logger logger = context.getLogger("com.company.test");
            logger.info("handled request");
        } finally {
            MDC.remove("correlationId");
        }

        String output = captured.toString(StandardCharsets.UTF_8);

        assertThat(output).contains("corr-123");
    }
}
