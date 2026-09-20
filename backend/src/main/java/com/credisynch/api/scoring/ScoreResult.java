package com.credisynch.api.scoring;

import java.util.List;

/** Model output, plus a flag telling the policy engine whether the model actually answered. */
public record ScoreResult(
        double fraudProbability,
        double noveltyScore,
        List<ReasonCode> reasonCodes,
        String modelVersion,
        boolean degraded) {

    /** Used when the model service is unreachable: no score, conservative handling downstream. */
    public static ScoreResult degradedResult() {
        return new ScoreResult(
                Double.NaN,
                0.0,
                List.of(
                        new ReasonCode(
                                "MODEL_UNAVAILABLE",
                                "model_service",
                                0.0,
                                "INCREASES_RISK")),
                "unavailable",
                true);
    }
}