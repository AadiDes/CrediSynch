package com.credisynch.api.cases;

import com.credisynch.api.persistence.CaseEmbeddingRepository;
import com.credisynch.api.scoring.ReasonCode;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * ADR 0003: embeds a case's evidence for similar-case retrieval. Runs off the synchronous decision
 * path ({@code @Async}, per docs/architecture.md's latency budget) - a submission's response never
 * waits on a Bedrock round trip. A failed or skipped embedding just leaves the case unindexed for
 * similarity search; nothing else about the case is affected.
 */
@Service
public class CaseEmbeddingService {

    private final EmbeddingClient embeddingClient;
    private final CaseEmbeddingRepository embeddings;

    public CaseEmbeddingService(EmbeddingClient embeddingClient, CaseEmbeddingRepository embeddings) {
        this.embeddingClient = embeddingClient;
        this.embeddings = embeddings;
    }

    @Async
    public void embedAsync(UUID caseId, String action, List<ReasonCode> reasonCodes) {
        embeddingClient.embed(narrativeOf(action, reasonCodes)).ifPresent(vector -> embeddings.upsert(caseId, vector));
    }

    private String narrativeOf(String action, List<ReasonCode> reasonCodes) {
        StringBuilder text = new StringBuilder("action: ").append(action);
        for (ReasonCode rc : reasonCodes) {
            text.append("; ").append(rc.feature()).append(": ")
                    .append("%+.4f".formatted(rc.contribution())).append(" (").append(rc.direction()).append(')');
        }
        return text.toString();
    }
}
