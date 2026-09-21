package com.credisynch.api.cases;

import com.credisynch.api.persistence.CaseEmbeddingRepository;
import com.credisynch.api.persistence.CaseRecords.CaseSummaryRow;
import com.credisynch.api.persistence.CaseRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * ADR 0003's similar-case search: nearest neighbours by embedding cosine distance. No embedding
 * pipeline is wired in yet, so a case with no stored embedding simply has no similar cases - a real
 * empty result, not an error, and no external call is attempted.
 */
@Component
public class SimilarCaseFinder {

    private final CaseEmbeddingRepository embeddings;
    private final CaseRepository cases;

    public SimilarCaseFinder(CaseEmbeddingRepository embeddings, CaseRepository cases) {
        this.embeddings = embeddings;
        this.cases = cases;
    }

    public List<CaseSummaryRow> findSimilar(UUID caseId, int limit) {
        if (!embeddings.hasEmbedding(caseId)) {
            return List.of();
        }
        return embeddings.findNearest(caseId, limit).stream()
                .map(cases::findSummary)
                .flatMap(java.util.Optional::stream)
                .toList();
    }
}
