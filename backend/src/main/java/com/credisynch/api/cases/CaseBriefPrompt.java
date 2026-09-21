package com.credisynch.api.cases;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * The single versioned prompt (ADR 0005: "prompt templates are versioned in the repository") every
 * brief provider renders identically - the wording of the grounding rules must not drift between
 * providers, only which model answers them.
 */
@Component
public class CaseBriefPrompt {

    private final String template;

    public CaseBriefPrompt() {
        this.template = load();
    }

    private String load() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("prompts/case-brief-v1.txt").getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing prompts/case-brief-v1.txt on the classpath", e);
        }
    }

    public String render(CaseNarrativeGenerator.Context context) {
        String reasonCodes = context.reasonCodes().stream()
                .map(rc -> "- %s: %+.4f (%s)".formatted(rc.feature(), rc.contribution(), rc.direction()))
                .collect(Collectors.joining("\n"));
        return template
                .replace("{{action}}", context.action())
                .replace("{{fraudProbability}}",
                        context.fraudProbability() == null ? "unavailable" : "%.4f".formatted(context.fraudProbability()))
                .replace("{{reasonCodes}}", reasonCodes.isEmpty() ? "(none)" : reasonCodes)
                .replace("{{linkedApplicationCount}}", String.valueOf(context.linkedApplications().size()));
    }
}
