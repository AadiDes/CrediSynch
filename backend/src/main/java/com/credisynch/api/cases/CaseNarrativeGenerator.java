package com.credisynch.api.cases;

import java.util.Optional;
import java.util.UUID;

/**
 * The LLM boundary (ADR 0005): writes the analyst brief from reason codes and graph evidence only,
 * never from applicant free text, and never decides anything - it explains a decision already made.
 *
 * No implementation is wired in yet (no Bedrock/LLM provider is provisioned for this deployment).
 * {@link NullCaseNarrativeGenerator} is the default bean so the rest of the case pipeline - queue,
 * detail, labels - works completely without one. Swap in a real implementation by defining another
 * {@code CaseNarrativeGenerator} bean and marking it {@code @Primary}; nothing else changes.
 */
public interface CaseNarrativeGenerator {

    record Narrative(String brief, String model) {}

    record Context(UUID caseId, String action, Double fraudProbability, java.util.List<ReasonCodeView> reasonCodes,
                   java.util.List<UUID> linkedApplications) {}

    record ReasonCodeView(String code, String feature, double contribution, String direction) {}

    /** Empty when no brief could be (or was) generated; the case still works, just without one. */
    Optional<Narrative> generate(Context context);
}
