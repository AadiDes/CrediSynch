package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.credisynch.api.cases.BedrockCaseNarrativeGenerator;
import com.credisynch.api.cases.CaseBriefPrompt;
import com.credisynch.api.cases.CaseNarrativeGenerator.Context;
import com.credisynch.api.cases.CaseNarrativeGenerator.Narrative;
import com.credisynch.api.cases.CaseNarrativeGenerator.ReasonCodeView;
import com.credisynch.api.cases.TemplateCaseNarrativeGenerator;
import com.credisynch.api.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

class BedrockCaseNarrativeGeneratorTest {

    private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);
    private final TemplateCaseNarrativeGenerator template = new TemplateCaseNarrativeGenerator();

    private BedrockCaseNarrativeGenerator generator() {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ml("http://localhost:8000", 400),
                new AppProperties.Hashing("test-key"),
                new AppProperties.Policy("policy-test", 1500.0, 3.0, 0.8, 5.0, 0.1, 150.0),
                new AppProperties.Graph(720, 1, 3),
                new AppProperties.Restricted(50000, 3, 90, 0.45),
                new AppProperties.Bedrock("apac.amazon.nova-lite-v1:0", "amazon.titan-embed-text-v2:0"),
                new AppProperties.Llm("bedrock", null, "gemini-2.5-flash", "gemini-embedding-001"));
        return new BedrockCaseNarrativeGenerator(bedrock, template, new CaseBriefPrompt(), new ObjectMapper(), properties);
    }

    private Context context() {
        return new Context(UUID.randomUUID(), "REVIEW", 0.6,
                List.of(new ReasonCodeView("VELOCITY_6H", "velocity_6h", 0.5, "INCREASES_RISK")), List.of());
    }

    @Test
    @DisplayName("a well-formed Nova response is returned as the brief, tagged with the model id")
    void wellFormedResponseIsUsed() {
        String responseJson = """
                {"output":{"message":{"content":[{"text":"A concise analyst summary."}]}}}
                """;
        given(bedrock.invokeModel(any(InvokeModelRequest.class))).willReturn(InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String(responseJson))
                .build());

        Narrative narrative = generator().generate(context()).orElseThrow();

        assertThat(narrative.brief()).isEqualTo("A concise analyst summary.");
        assertThat(narrative.model()).isEqualTo("apac.amazon.nova-lite-v1:0");
    }

    @Test
    @DisplayName("any Bedrock failure falls back to the template brief instead of surfacing an error")
    void bedrockFailureFallsBackToTemplate() {
        given(bedrock.invokeModel(any(InvokeModelRequest.class)))
                .willThrow(software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException.builder()
                        .message("no model access").build());

        Narrative narrative = generator().generate(context()).orElseThrow();

        assertThat(narrative.model()).isEqualTo("template-v1");
        assertThat(narrative.brief()).contains("REVIEW");
    }

    @Test
    @DisplayName("an empty or malformed response also falls back to the template brief")
    void emptyResponseFallsBackToTemplate() {
        given(bedrock.invokeModel(any(InvokeModelRequest.class))).willReturn(InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String("{}"))
                .build());

        Narrative narrative = generator().generate(context()).orElseThrow();

        assertThat(narrative.model()).isEqualTo("template-v1");
    }
}
