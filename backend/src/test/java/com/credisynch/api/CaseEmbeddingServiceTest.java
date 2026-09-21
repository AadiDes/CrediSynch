package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.credisynch.api.cases.CaseEmbeddingService;
import com.credisynch.api.cases.EmbeddingClient;
import com.credisynch.api.persistence.CaseEmbeddingRepository;
import com.credisynch.api.scoring.ReasonCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CaseEmbeddingServiceTest {

    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final CaseEmbeddingRepository embeddings = mock(CaseEmbeddingRepository.class);
    private final CaseEmbeddingService service = new CaseEmbeddingService(embeddingClient, embeddings);

    @Test
    @DisplayName("a successful embedding is stored against the case id")
    void successfulEmbeddingIsStored() {
        UUID caseId = UUID.randomUUID();
        float[] vector = new float[]{0.1f, 0.2f};
        given(embeddingClient.embed(anyString())).willReturn(Optional.of(vector));

        service.embedAsync(caseId, "REVIEW", List.of(new ReasonCode("VELOCITY_6H", "velocity_6h", 0.5, "INCREASES_RISK")));

        verify(embeddings).upsert(caseId, vector);
    }

    @Test
    @DisplayName("no embedding (Bedrock unreachable) writes nothing, and is not an error")
    void noEmbeddingWritesNothing() {
        given(embeddingClient.embed(anyString())).willReturn(Optional.empty());

        service.embedAsync(UUID.randomUUID(), "REVIEW", List.of());

        verify(embeddings, never()).upsert(any(), any());
    }

    @Test
    @DisplayName("the embedded text is grounded in the action and reason codes it was given")
    void embeddedTextIsGrounded() {
        given(embeddingClient.embed(anyString())).willReturn(Optional.empty());
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

        service.embedAsync(UUID.randomUUID(), "DECLINE",
                List.of(new ReasonCode("CREDIT_RISK_SCORE", "credit_risk_score", 0.9, "INCREASES_RISK")));

        verify(embeddingClient).embed(textCaptor.capture());
        assertThat(textCaptor.getValue()).contains("DECLINE").contains("credit_risk_score");
    }
}
