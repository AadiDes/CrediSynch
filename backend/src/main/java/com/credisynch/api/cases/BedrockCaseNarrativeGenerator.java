package com.credisynch.api.cases;

import com.credisynch.api.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * ADR 0005: writes the analyst brief with Amazon Nova, grounded only in reason codes and graph
 * evidence via the versioned prompt in {@code prompts/case-brief-v1.txt} - applicant free text
 * never reaches the prompt.
 *
 * docs/architecture.md's documented degraded mode ("Bedrock unavailable | Template-generated
 * brief") is this class's own failure path: any error from Bedrock - no model access, throttling,
 * a network blip - falls back to {@link TemplateCaseNarrativeGenerator} rather than surfacing an
 * error, so the case pipeline is never blocked on Bedrock being reachable.
 */
@Primary
@Component
public class BedrockCaseNarrativeGenerator implements CaseNarrativeGenerator {

    private static final Logger log = LoggerFactory.getLogger(BedrockCaseNarrativeGenerator.class);

    private final BedrockRuntimeClient bedrock;
    private final TemplateCaseNarrativeGenerator fallback;
    private final ObjectMapper objectMapper;
    private final String modelId;
    private final String promptTemplate;

    public BedrockCaseNarrativeGenerator(BedrockRuntimeClient bedrock, TemplateCaseNarrativeGenerator fallback,
                                         ObjectMapper objectMapper, AppProperties properties) {
        this.bedrock = bedrock;
        this.fallback = fallback;
        this.objectMapper = objectMapper;
        this.modelId = properties.bedrock().briefModelId();
        this.promptTemplate = loadPromptTemplate();
    }

    private String loadPromptTemplate() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("prompts/case-brief-v1.txt").getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing prompts/case-brief-v1.txt on the classpath", e);
        }
    }

    @Override
    public Optional<Narrative> generate(Context context) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "messages", List.of(Map.of("role", "user", "content", List.of(Map.of("text", renderPrompt(context))))),
                    "inferenceConfig", Map.of("maxTokens", 300, "temperature", 0.2)));

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(body))
                    .build();
            InvokeModelResponse response = bedrock.invokeModel(request);

            JsonNode root = objectMapper.readTree(response.body().asUtf8String());
            String text = root.path("output").path("message").path("content").path(0).path("text").asText("");
            if (text.isBlank()) {
                throw new IllegalStateException("Bedrock returned no brief text");
            }
            return Optional.of(new Narrative(text.trim(), modelId));
        } catch (Exception e) {
            log.warn("Bedrock brief generation failed for case {}; falling back to the template brief",
                    context.caseId(), e);
            return fallback.generate(context);
        }
    }

    private String renderPrompt(Context context) {
        String reasonCodes = context.reasonCodes().stream()
                .map(rc -> "- %s: %+.4f (%s)".formatted(rc.feature(), rc.contribution(), rc.direction()))
                .collect(Collectors.joining("\n"));
        return promptTemplate
                .replace("{{action}}", context.action())
                .replace("{{fraudProbability}}",
                        context.fraudProbability() == null ? "unavailable" : "%.4f".formatted(context.fraudProbability()))
                .replace("{{reasonCodes}}", reasonCodes.isEmpty() ? "(none)" : reasonCodes)
                .replace("{{linkedApplicationCount}}", String.valueOf(context.linkedApplications().size()));
    }
}
