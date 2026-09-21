package com.credisynch.api.cases;

import com.credisynch.api.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Titan Text Embeddings V2, fixed at 1024 dimensions (ADR 0003) - invoked directly, no cross-region
 * profile needed. Active when {@code app.llm.provider} is "bedrock" (the default); see
 * {@link GeminiEmbeddingClient} for the substitute provider.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "bedrock", matchIfMissing = true)
public class BedrockTitanEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockTitanEmbeddingClient.class);
    private static final int DIMENSIONS = 1024;

    private final BedrockRuntimeClient bedrock;
    private final ObjectMapper objectMapper;
    private final String modelId;

    public BedrockTitanEmbeddingClient(BedrockRuntimeClient bedrock, ObjectMapper objectMapper,
                                       AppProperties properties) {
        this.bedrock = bedrock;
        this.objectMapper = objectMapper;
        this.modelId = properties.bedrock().embeddingModelId();
    }

    @Override
    public Optional<float[]> embed(String text) {
        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("inputText", text, "dimensions", DIMENSIONS, "normalize", true));
            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(body))
                    .build();
            InvokeModelResponse response = bedrock.invokeModel(request);

            JsonNode root = objectMapper.readTree(response.body().asUtf8String());
            JsonNode values = root.path("embedding");
            if (!values.isArray() || values.size() != DIMENSIONS) {
                throw new IllegalStateException(
                        "Titan returned %d dimensions, expected %d".formatted(values.size(), DIMENSIONS));
            }
            float[] embedding = new float[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) {
                embedding[i] = (float) values.get(i).asDouble();
            }
            return Optional.of(embedding);
        } catch (Exception e) {
            log.warn("Titan embedding failed; the case will simply have no embedding yet", e);
            return Optional.empty();
        }
    }
}
