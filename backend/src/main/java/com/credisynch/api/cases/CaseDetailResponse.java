package com.credisynch.api.cases;

import com.credisynch.api.scoring.ReasonCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaseDetailResponse(
        UUID caseId,
        UUID applicationId,
        String status,
        int priority,
        String action,
        Double fraudProbability,
        UUID ringId,
        Instant openedAt,
        List<ReasonCode> reasonCodes,
        List<UUID> linkedApplications,
        RingResponse ring,
        String brief,
        String briefModel,
        List<CaseSummaryResponse> similarCases) {}
