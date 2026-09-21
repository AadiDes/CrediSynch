package com.credisynch.api.cases;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Cases", description = "Analyst case queue: list, detail, labels")
public class CasesController {

    private final CaseService caseService;

    public CasesController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping("/queue/summary")
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(summary = "Analyst-only: case queue summary")
    public QueueSummaryResponse queueSummary() {
        return caseService.queueSummary();
    }

    @GetMapping("/cases")
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(summary = "List fraud cases")
    public CasePageResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int limit) {
        return caseService.list(status, cursor, Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping("/cases/{caseId}")
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(summary = "Case detail with reason codes, linked entities and the generated brief")
    public ResponseEntity<CaseDetailResponse> detail(@PathVariable UUID caseId) {
        return caseService.detail(caseId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/cases/{caseId}/labels")
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(summary = "Record an analyst label; the fast feedback signal for retraining")
    public ResponseEntity<Void> label(@PathVariable UUID caseId, @Valid @RequestBody LabelRequest request,
                                      Authentication authentication) {
        boolean recorded = caseService.label(caseId, request, authentication.getName());
        return recorded ? ResponseEntity.status(201).build() : ResponseEntity.notFound().build();
    }
}
