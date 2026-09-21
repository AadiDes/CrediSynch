package com.credisynch.api.restricted;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/confirmations")
@Tag(name = "Transactions", description = "Module A: merchant-locked cards and post-approval spend")
public class ConfirmationsController {

    private final ConfirmationService confirmationService;

    public ConfirmationsController(ConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    @PostMapping("/{confirmationId}")
    @PreAuthorize("hasAnyRole('APPLICANT','ANALYST','ADMIN')")
    @Operation(summary = "Customer answers 'was this you?'; the answer becomes a label within minutes")
    public void answer(@PathVariable UUID confirmationId, @Valid @RequestBody ConfirmationAnswerRequest request,
                       Authentication authentication) {
        confirmationService.answer(confirmationId, request.answer(), authentication.getName());
    }
}
