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
 * Gemini's embedding API, requested at 1024 dimensions to match this schema's fixed
 * {@code vector(1024)} columns (ADR 0003) - a temporary substitute for Titan while AWS Bedrock
 * access is pending; see {@link BedrockTitanEmbeddingClient}.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "gemini")
public class GeminiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingClient.class);
    private static final int DIMENSIONS = 1024;

    private final RestClient gemini;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiEmbeddingClient(RestClient geminiRestClient, ObjectMapper objectMapper, AppProperties properties) {
        this.gemini = geminiRestClient;
        this.objectMapper = objectMapper;
        this.apiKey = properties.llm().geminiApiKey();
        this.model = properties.llm().geminiEmbeddingModel();
    }

    @Override
    public Optional<float[]> embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No GEMINI_API_KEY configured; the case will simply have no embedding yet");
            return Optional.empty();
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "content", Map.of("parts", List.of(Map.of("text", text))),
                    "outputDimensionality", DIMENSIONS));

            String responseJson = gemini.post()
                    .uri("/v1beta/models/{model}:embedContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode values = objectMapper.readTree(responseJson).path("embedding").path("values");
            if (!values.isArray() || values.size() != DIMENSIONS) {
                throw new IllegalStateException(
                        "Gemini returned %d dimensions, expected %d".formatted(values.size(), DIMENSIONS));
            }
            float[] embedding = new float[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) {
                embedding[i] = (float) values.get(i).asDouble();
            }
            return Optional.of(embedding);
        } catch (Exception e) {
            log.warn("Gemini embedding failed; the case will simply have no embedding yet", e);
            return Optional.empty();
        }
    }
}
