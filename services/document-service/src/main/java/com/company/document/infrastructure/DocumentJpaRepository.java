package com.company.document.infrastructure;

import com.company.document.domain.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentJpaRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByCustomerId(UUID customerId, Pageable pageable);
}
