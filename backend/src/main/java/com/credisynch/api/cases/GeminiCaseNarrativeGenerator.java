package com.credisynch.api.cases;

import com.credisynch.api.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * ADR 0005 says "AWS Bedrock or equivalent LLM service" - this is that substitute, active when
 * {@code app.llm.provider=gemini}. A temporary stand-in while AWS Bedrock model access is pending
 * on this account (a new-account review, unrelated to the code): same {@link CaseNarrativeGenerator}
 * contract, same versioned prompt ({@link CaseBriefPrompt}), same grounding rules, same
 * template-brief fallback on any failure. Switching back to Bedrock is a config change.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "gemini")
public class GeminiCaseNarrativeGenerator implements CaseNarrativeGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeminiCaseNarrativeGenerator.class);

    private final RestClient gemini;
    private final TemplateCaseNarrativeGenerator fallback;
    private final CaseBriefPrompt prompt;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiCaseNarrativeGenerator(RestClient geminiRestClient, TemplateCaseNarrativeGenerator fallback,
                                        CaseBriefPrompt prompt, ObjectMapper objectMapper, AppProperties properties) {
        this.gemini = geminiRestClient;
        this.fallback = fallback;
        this.prompt = prompt;
        this.objectMapper = objectMapper;
        this.apiKey = properties.llm().geminiApiKey();
        this.model = properties.llm().geminiBriefModel();
    }

    @Override
    public Optional<Narrative> generate(Context context) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No GEMINI_API_KEY configured; falling back to the template brief");
            return fallback.generate(context);
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt.render(context))))),
                    "generationConfig", Map.of("maxOutputTokens", 300, "temperature", 0.2)));

            String responseJson = gemini.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                throw new IllegalStateException("Gemini returned no brief text");
            }
            return Optional.of(new Narrative(text.trim(), model));
        } catch (Exception e) {
            log.warn("Gemini brief generation failed for case {}; falling back to the template brief",
                    context.caseId(), e);
            return fallback.generate(context);
        }
    }
}
