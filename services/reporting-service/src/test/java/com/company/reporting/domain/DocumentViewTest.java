package com.company.reporting.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentViewTest {

    @Test
    void newDocumentViewStartsUploaded() {
        var view = new DocumentView(UUID.randomUUID(), UUID.randomUUID(), "PASSPORT", Instant.now());

        assertThat(view.getStatus()).isEqualTo(DocumentReportStatus.UPLOADED);
        assertThat(view.getDocumentType()).isEqualTo("PASSPORT");
        assertThat(view.getReviewedAt()).isNull();
    }

    @Test
    void reviewTransitionsToVerified() {
        var view = new DocumentView(UUID.randomUUID(), UUID.randomUUID(), "NATIONAL_ID", Instant.now());
        Instant reviewedAt = Instant.now();

        view.review(DocumentReportStatus.VERIFIED, null, reviewedAt);

        assertThat(view.getStatus()).isEqualTo(DocumentReportStatus.VERIFIED);
        assertThat(view.getReviewedAt()).isEqualTo(reviewedAt);
    }

    @Test
    void reviewTransitionsToRejectedWithNotes() {
        var view = new DocumentView(UUID.randomUUID(), UUID.randomUUID(), "BANK_STATEMENT", Instant.now());

        view.review(DocumentReportStatus.REJECTED, "Illegible scan", Instant.now());

        assertThat(view.getStatus()).isEqualTo(DocumentReportStatus.REJECTED);
        assertThat(view.getNotes()).isEqualTo("Illegible scan");
    }
}
