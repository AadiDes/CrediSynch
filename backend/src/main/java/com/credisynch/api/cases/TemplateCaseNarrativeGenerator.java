package com.credisynch.api.cases;

import com.credisynch.api.cases.CaseNarrativeGenerator.Context;
import com.credisynch.api.cases.CaseNarrativeGenerator.Narrative;
import com.credisynch.api.cases.CaseNarrativeGenerator.ReasonCodeView;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The documented degraded mode (docs/architecture.md: "Bedrock unavailable | Template-generated
 * brief"), and every LLM provider's own failure path. Deterministic, no external call, and
 * grounded in exactly the two things ADR 0005 allows a brief to cite: reason codes and graph
 * evidence - never applicant free text.
 *
 * Deliberately does NOT implement {@link CaseNarrativeGenerator}: it is never injected as "the"
 * generator (that ambiguity is exactly what broke Spring startup once already), only ever wrapped
 * by whichever provider is active - see {@link BedrockCaseNarrativeGenerator} and
 * {@link GeminiCaseNarrativeGenerator}, which fall back to it by concrete-class reference.
 */
@Component
public class TemplateCaseNarrativeGenerator {

    private static final String MODEL_LABEL = "template-v1";

    public Optional<Narrative> generate(Context context) {
        StringBuilder brief = new StringBuilder();
        brief.append(context.action());
        if (context.fraudProbability() != null) {
            brief.append(" at a fraud probability of %.2f%%".formatted(context.fraudProbability() * 100));
        }
        brief.append(".");

        String increasing = joinReasons(context.reasonCodes(), "INCREASES_RISK");
        String decreasing = joinReasons(context.reasonCodes(), "DECREASES_RISK");
        if (!increasing.isEmpty()) {
            brief.append(" Signals increasing risk: ").append(increasing).append(".");
        }
        if (!decreasing.isEmpty()) {
            brief.append(" Signals decreasing risk: ").append(decreasing).append(".");
        }

        int linked = context.linkedApplications().size();
        brief.append(linked == 0
                ? " No shared identities with other applications."
                : " Shares a device, phone, email, address or bank account with %d other application(s)."
                        .formatted(linked));

        return Optional.of(new Narrative(brief.toString(), MODEL_LABEL));
    }

    private String joinReasons(List<ReasonCodeView> reasonCodes, String direction) {
        return reasonCodes.stream()
                .filter(rc -> direction.equals(rc.direction()))
                .map(rc -> "%s (%+.4f)".formatted(rc.feature(), rc.contribution()))
                .collect(Collectors.joining(", "));
    }
}
