package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.credisynch.api.cases.CaseBriefPrompt;
import com.credisynch.api.cases.CaseNarrativeGenerator.Context;
import com.credisynch.api.cases.CaseNarrativeGenerator.Narrative;
import com.credisynch.api.cases.CaseNarrativeGenerator.ReasonCodeView;
import com.credisynch.api.cases.GeminiCaseNarrativeGenerator;
import com.credisynch.api.cases.TemplateCaseNarrativeGenerator;
import com.credisynch.api.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.web.client.RestClient;

class GeminiCaseNarrativeGeneratorTest {

    private final RestClient gemini = mock(RestClient.class, Answers.RETURNS_DEEP_STUBS);
    private final TemplateCaseNarrativeGenerator template = new TemplateCaseNarrativeGenerator();

    private GeminiCaseNarrativeGenerator generator(String apiKey) {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ml("http://localhost:8000", 400),
                new AppProperties.Hashing("test-key"),
                new AppProperties.Policy("policy-test", 1500.0, 3.0, 0.8, 5.0, 0.1, 150.0),
                new AppProperties.Graph(720, 1, 3),
                new AppProperties.Restricted(50000, 3, 90, 0.45, 0.75),
                new AppProperties.Bedrock("apac.amazon.nova-lite-v1:0", "amazon.titan-embed-text-v2:0"),
                new AppProperties.Llm("gemini", apiKey, "gemini-2.5-flash", "gemini-embedding-001"));
        return new GeminiCaseNarrativeGenerator(gemini, template, new CaseBriefPrompt(), new ObjectMapper(), properties);
    }

    private Context context() {
        return new Context(UUID.randomUUID(), "REVIEW", 0.6,
                List.of(new ReasonCodeView("VELOCITY_6H", "velocity_6h", 0.5, "INCREASES_RISK")), List.of());
    }

    @Test
    @DisplayName("a well-formed Gemini response is returned as the brief, tagged with the model name")
    void wellFormedResponseIsUsed() {
        String responseJson = """
                {"candidates":[{"content":{"parts":[{"text":"A concise analyst summary."}]}}]}
                """;
        given(gemini.post().uri(anyString(), any(), any()).contentType(any()).body(anyString())
                .retrieve().body(String.class)).willReturn(responseJson);

        Narrative narrative = generator("fake-key").generate(context()).orElseThrow();

        assertThat(narrative.brief()).isEqualTo("A concise analyst summary.");
        assertThat(narrative.model()).isEqualTo("gemini-2.5-flash");
    }

    @Test
    @DisplayName("no API key configured falls back to the template brief without an HTTP call")
    void noApiKeyFallsBackToTemplate() {
        Narrative narrative = generator(null).generate(context()).orElseThrow();

        assertThat(narrative.model()).isEqualTo("template-v1");
    }

    @Test
    @DisplayName("a malformed response falls back to the template brief")
    void malformedResponseFallsBackToTemplate() {
        given(gemini.post().uri(anyString(), any(), any()).contentType(any()).body(anyString())
                .retrieve().body(String.class)).willReturn("{}");

        Narrative narrative = generator("fake-key").generate(context()).orElseThrow();

        assertThat(narrative.model()).isEqualTo("template-v1");
    }
}
