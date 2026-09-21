package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.credisynch.api.cases.GeminiEmbeddingClient;
import com.credisynch.api.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.web.client.RestClient;

class GeminiEmbeddingClientTest {

    private final RestClient gemini = mock(RestClient.class, Answers.RETURNS_DEEP_STUBS);

    private GeminiEmbeddingClient client(String apiKey) {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ml("http://localhost:8000", 400),
                new AppProperties.Hashing("test-key"),
                new AppProperties.Policy("policy-test", 1500.0, 3.0, 0.8, 5.0, 0.1, 150.0),
                new AppProperties.Graph(720, 1, 3),
                new AppProperties.Restricted(50000, 3, 90, 0.45),
                new AppProperties.Bedrock("apac.amazon.nova-lite-v1:0", "amazon.titan-embed-text-v2:0"),
                new AppProperties.Llm("gemini", apiKey, "gemini-2.5-flash", "gemini-embedding-001"));
        return new GeminiEmbeddingClient(gemini, new ObjectMapper(), properties);
    }

    private String vectorJson(int dimensions) {
        String values = IntStream.range(0, dimensions).mapToObj(i -> "0.02").collect(Collectors.joining(","));
        return "{\"embedding\":{\"values\":[" + values + "]}}";
    }

    @Test
    @DisplayName("a well-formed 1024-dimension response is returned")
    void wellFormedResponseIsReturned() {
        given(gemini.post().uri(anyString(), any(), any()).contentType(any()).body(anyString())
                .retrieve().body(String.class)).willReturn(vectorJson(1024));

        Optional<float[]> embedding = client("fake-key").embed("some case narrative");

        assertThat(embedding).isPresent();
        assertThat(embedding.get()).hasSize(1024);
    }

    @Test
    @DisplayName("no API key configured returns empty without an HTTP call")
    void noApiKeyReturnsEmpty() {
        assertThat(client(null).embed("text")).isEmpty();
    }

    @Test
    @DisplayName("the wrong dimension count is rejected, not silently truncated")
    void wrongDimensionCountIsRejected() {
        given(gemini.post().uri(anyString(), any(), any()).contentType(any()).body(anyString())
                .retrieve().body(String.class)).willReturn(vectorJson(5));

        assertThat(client("fake-key").embed("text")).isEmpty();
    }
}
