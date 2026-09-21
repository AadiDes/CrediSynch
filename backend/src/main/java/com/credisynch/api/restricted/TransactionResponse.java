package com.credisynch.api.restricted;

import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        String outcome,
        String matchedPartnerId,
        Double matchScore,
        String matchMethod,
        UUID confirmationId) {}
