package com.credisynch.api.decision;

import com.credisynch.api.scoring.ReasonCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DecisionResponse(
        UUID applicationId,
        UUID decisionId,
        DecisionAction action,
        String customerMessage,
        Double fraudProbability,
        Double graphRisk,
        Double noveltyScore,
        List<ReasonCode> reasonCodes,
        List<String> rulesFired,
        String modelVersion,
        String policyVersion,
        boolean degradedMode,
        long latencyMs,
        Instant decidedAt) {}
