package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.credisynch.api.cases.BedrockTitanEmbeddingClient;
import com.credisynch.api.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

class BedrockTitanEmbeddingClientTest {

    private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);

    private BedrockTitanEmbeddingClient client() {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ml("http://localhost:8000", 400),
                new AppProperties.Hashing("test-key"),
                new AppProperties.Policy("policy-test", 1500.0, 3.0, 0.8, 5.0, 0.1, 150.0),
                new AppProperties.Graph(720, 1, 3),
                new AppProperties.Restricted(50000, 3, 90, 0.45),
                new AppProperties.Bedrock("apac.amazon.nova-lite-v1:0", "amazon.titan-embed-text-v2:0"));
        return new BedrockTitanEmbeddingClient(bedrock, new ObjectMapper(), properties);
    }

    private String vectorJson(int dimensions) {
        String values = IntStream.range(0, dimensions).mapToObj(i -> "0.01").collect(Collectors.joining(","));
        return "{\"embedding\":[" + values + "],\"inputTextTokenCount\":5}";
    }

    @Test
    @DisplayName("a well-formed 1024-dimension response is returned")
    void wellFormedResponseIsReturned() {
        given(bedrock.invokeModel(any(InvokeModelRequest.class))).willReturn(InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String(vectorJson(1024)))
                .build());

        Optional<float[]> embedding = client().embed("some case narrative");

        assertThat(embedding).isPresent();
        assertThat(embedding.get()).hasSize(1024);
        assertThat(embedding.get()[0]).isEqualTo(0.01f);
    }

    @Test
    @DisplayName("a response with the wrong dimension count is rejected, not silently truncated")
    void wrongDimensionCountIsRejected() {
        given(bedrock.invokeModel(any(InvokeModelRequest.class))).willReturn(InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String(vectorJson(3)))
                .build());

        assertThat(client().embed("text")).isEmpty();
    }

    @Test
    @DisplayName("any Bedrock failure returns empty, never throws")
    void bedrockFailureReturnsEmpty() {
        given(bedrock.invokeModel(any(InvokeModelRequest.class)))
                .willThrow(ThrottlingException.builder().message("too many requests").build());

        assertThat(client().embed("text")).isEmpty();
    }
}
