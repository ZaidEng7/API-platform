package com.company.document.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTest {

    @Test
    void newDocumentStartsUploaded() {
        var document = new Document(UUID.randomUUID(), UUID.randomUUID(), DocumentType.PASSPORT, "dms://ref-1",
                Instant.now());

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(document.getNotes()).isNull();
    }

    @Test
    void verifySetsStatusAndNotes() {
        var document = new Document(UUID.randomUUID(), UUID.randomUUID(), DocumentType.PASSPORT, "dms://ref-1",
                Instant.now());

        document.verify("Clear scan, matches name on file");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.VERIFIED);
        assertThat(document.getNotes()).isEqualTo("Clear scan, matches name on file");
    }

    @Test
    void rejectSetsStatusAndNotes() {
        var document = new Document(UUID.randomUUID(), UUID.randomUUID(), DocumentType.PASSPORT, "dms://ref-1",
                Instant.now());

        document.reject("Expired");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.REJECTED);
        assertThat(document.getNotes()).isEqualTo("Expired");
    }

    @Test
    void reviewingAnAlreadyReviewedDocumentThrows() {
        var document = new Document(UUID.randomUUID(), UUID.randomUUID(), DocumentType.PASSPORT, "dms://ref-1",
                Instant.now());
        document.verify("Clear scan");

        assertThatThrownBy(() -> document.reject("changed my mind"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already reviewed");
    }
}
