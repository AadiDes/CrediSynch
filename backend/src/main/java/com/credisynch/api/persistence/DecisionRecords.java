package com.credisynch.api.persistence;

import com.credisynch.api.decision.DecisionAction;
import java.time.Instant;
import java.util.UUID;

/** Row shapes used by the repositories. Persistence is SQL-first (ADR 0008), so these mirror the schema. */
public final class DecisionRecords {

    private DecisionRecords() {}

    public record ApplicationRow(
            UUID id,
            String externalRef,
            String channel,
            String partnerId,
            String applicantNameHash,
            String featuresJson,
            String rawPayloadJson) {}

    public record DecisionRow(
            UUID id,
            UUID applicationId,
            DecisionAction action,
            Double fraudProbability,
            Double graphRisk,
            Double noveltyScore,
            String rulesFiredJson,
            String reasonCodesJson,
            String modelVersion,
            String policyVersion,
            long latencyMs,
            boolean degradedMode,
            Instant decidedAt) {}
}
