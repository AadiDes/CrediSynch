package com.credisynch.api.decision;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Decisioning", description = "Synchronous fraud decisions on credit applications")
public class DecisionController {

    private final DecisionService decisionService;
    private final RingDetectionService ringDetection;

    public DecisionController(DecisionService decisionService, RingDetectionService ringDetection) {
        this.decisionService = decisionService;
        this.ringDetection = ringDetection;
    }

    @PostMapping("/applications")
    @PreAuthorize("hasAnyRole('APPLICANT','ANALYST','ADMIN')")
    @Operation(summary = "Submit an application and receive a decision synchronously")
    public DecisionResponse submit(
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128) String idempotencyKey,
            @Valid @RequestBody ApplicationRequest request,
            Authentication authentication) {
        DecisionResponse response = decisionService.decide(request, idempotencyKey, authentication.getName());
        // Triggered post-commit (the @Transactional decide() call above has already returned/committed),
        // so the async ring query sees this application's just-inserted entity links.
        ringDetection.detectAsync(response.applicationId());
        return response;
    }

    @GetMapping("/policy/bands")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    @Operation(summary = "Show the live decision bands derived from the cost parameters")
    public List<String> bands() {
        return decisionService.describeBands();
    }

    @ExceptionHandler(DecisionService.IdempotencyConflict.class)
    ProblemDetail onIdempotencyConflict(DecisionService.IdempotencyConflict ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Idempotency key reuse");
        problem.setDetail(ex.getMessage());
        return problem;
    }
}
