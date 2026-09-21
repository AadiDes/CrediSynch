package com.credisynch.api.cases;

import java.time.Instant;
import java.util.UUID;

public record CaseSummaryResponse(
        UUID caseId,
        UUID applicationId,
        String status,
        int priority,
        String action,
        Double fraudProbability,
        UUID ringId,
        Instant openedAt) {}
