package com.credisynch.api.restricted;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Transactions", description = "Module A: merchant-locked cards and post-approval spend")
public class TransactionsController {

    private final TransactionService transactionService;

    public TransactionsController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAnyRole('APPLICANT','ANALYST','ADMIN')")
    @Operation(summary = "Authorise a purchase on an issued card (module A - merchant-locked cards)")
    public TransactionResponse authorise(
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128) String idempotencyKey,
            @Valid @RequestBody TransactionRequest request) {
        return transactionService.authorise(request, idempotencyKey);
    }
}
