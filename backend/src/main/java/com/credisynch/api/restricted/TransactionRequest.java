package com.credisynch.api.restricted;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TransactionRequest(
        @NotNull UUID cardAccountId,
        @Positive long amountMinor,
        @NotBlank @Size(max = 256) String rawDescriptor) {}
