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
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * ADR 0005: writes the analyst brief with Amazon Nova, grounded only in reason codes and graph
 * evidence via the versioned prompt in {@link CaseBriefPrompt} - applicant free text never reaches
 * the prompt. Active when {@code app.llm.provider} is "bedrock" (the default); see
 * {@link GeminiCaseNarrativeGenerator} for the substitute provider.
 *
 * docs/architecture.md's documented degraded mode ("Bedrock unavailable | Template-generated
 * brief") is this class's own failure path: any error from Bedrock - no model access, throttling,
 * a network blip - falls back to {@link TemplateCaseNarrativeGenerator} rather than surfacing an
 * error, so the case pipeline is never blocked on Bedrock being reachable.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "bedrock", matchIfMissing = true)
public class BedrockCaseNarrativeGenerator implements CaseNarrativeGenerator {

    private static final Logger log = LoggerFactory.getLogger(BedrockCaseNarrativeGenerator.class);

    private final BedrockRuntimeClient bedrock;
    private final TemplateCaseNarrativeGenerator fallback;
    private final CaseBriefPrompt prompt;
    private final ObjectMapper objectMapper;
    private final String modelId;

    public BedrockCaseNarrativeGenerator(BedrockRuntimeClient bedrock, TemplateCaseNarrativeGenerator fallback,
                                         CaseBriefPrompt prompt, ObjectMapper objectMapper, AppProperties properties) {
        this.bedrock = bedrock;
        this.fallback = fallback;
        this.prompt = prompt;
        this.objectMapper = objectMapper;
        this.modelId = properties.bedrock().briefModelId();
    }

    @Override
    public Optional<Narrative> generate(Context context) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "messages", List.of(Map.of("role", "user", "content", List.of(Map.of("text", prompt.render(context))))),
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
}
