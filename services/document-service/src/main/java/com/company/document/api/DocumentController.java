package com.company.document.api;

import com.company.document.api.dto.DocumentResponse;
import com.company.document.api.dto.DocumentReviewRequest;
import com.company.document.api.dto.UploadDocumentRequest;
import com.company.document.api.mapper.DocumentMapper;
import com.company.document.application.DocumentApplicationService;
import com.company.document.domain.Document;
import com.company.platform.web.response.ApiResponse;
import com.company.platform.web.response.PageMeta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Role gates follow the realm's own role descriptions
 * (platform/identity/realm-export.json), same rationale as KYC/AML
 * Service's controllers: "compliance" reviews (verifies/rejects) —
 * document review directly feeds KYC decisions (guide: "Document Service
 * (KYC needs it)"). Uploading metadata is open to the staff roles that
 * would plausibly register one on a Party's behalf.
 */
@Tag(name = "Documents", description = "Document metadata (guide §8.3 — references only, no file bytes)")
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentApplicationService documentApplicationService;
    private final DocumentMapper documentMapper;

    public DocumentController(DocumentApplicationService documentApplicationService, DocumentMapper documentMapper) {
        this.documentApplicationService = documentApplicationService;
        this.documentMapper = documentMapper;
    }

    @Operation(summary = "Get a document's metadata by id")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS', 'COMPLIANCE', 'CUSTOMER_SERVICE', 'AUDITOR')")
    public ApiResponse<DocumentResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(documentMapper.toResponse(documentApplicationService.getById(id)));
    }

    @Operation(summary = "List documents for a customer")
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATIONS', 'COMPLIANCE', 'CUSTOMER_SERVICE', 'AUDITOR')")
    public ApiResponse<List<DocumentResponse>> listByCustomer(
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending());
        Page<Document> result = documentApplicationService.listByCustomer(customerId, pageable);

        List<DocumentResponse> data = result.getContent().stream().map(documentMapper::toResponse).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        return ApiResponse.of(data, meta);
    }

    @Operation(summary = "Register a document's metadata (an opaque storage reference, never file bytes)")
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATIONS', 'COMPLIANCE', 'CUSTOMER_SERVICE')")
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(@Valid @RequestBody UploadDocumentRequest request) {
        var document = documentApplicationService.upload(request.customerId(), request.documentType(), request.storageReference());
        var response = documentMapper.toResponse(document);
        return ResponseEntity.created(URI.create("/api/v1/documents/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @Operation(summary = "Verify a document, once only")
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ApiResponse<DocumentResponse> verify(@PathVariable UUID id, @Valid @RequestBody DocumentReviewRequest request) {
        var document = documentApplicationService.verify(id, request.notes());
        return ApiResponse.of(documentMapper.toResponse(document));
    }

    @Operation(summary = "Reject a document, once only")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ApiResponse<DocumentResponse> reject(@PathVariable UUID id, @Valid @RequestBody DocumentReviewRequest request) {
        var document = documentApplicationService.reject(id, request.notes());
        return ApiResponse.of(documentMapper.toResponse(document));
    }
}
