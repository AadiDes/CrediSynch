package com.credisynch.api.persistence;

import java.time.Instant;
import java.util.UUID;

/** Row shapes for the analyst case queue. Mirrors the schema, SQL-first (ADR 0008). */
public final class CaseRecords {

    private CaseRecords() {}

    public record CaseSummaryRow(
            UUID caseId,
            UUID applicationId,
            String status,
            int priority,
            String action,
            Double fraudProbability,
            UUID ringId,
            Instant openedAt) {}

    public record CaseDetailRow(
            CaseSummaryRow summary,
            UUID decisionId,
            String reasonCodesJson,
            String brief,
            String briefModel) {}

    public record RingRow(UUID ringId, String algorithm, int size, Double density) {}

    public record QueueSummary(int openCount, int inReviewCount, int closedCount, java.util.Map<Integer, Integer> byPriority) {}
}
