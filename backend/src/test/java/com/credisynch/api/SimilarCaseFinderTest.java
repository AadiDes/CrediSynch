package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.credisynch.api.cases.SimilarCaseFinder;
import com.credisynch.api.persistence.CaseEmbeddingRepository;
import com.credisynch.api.persistence.CaseRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimilarCaseFinderTest {

    private final CaseEmbeddingRepository embeddings = mock(CaseEmbeddingRepository.class);
    private final CaseRepository cases = mock(CaseRepository.class);
    private final SimilarCaseFinder finder = new SimilarCaseFinder(embeddings, cases);

    @Test
    @DisplayName("a case with no stored embedding has no similar cases, and no kNN query is attempted")
    void noEmbeddingMeansNoSimilarCases() {
        UUID caseId = UUID.randomUUID();
        given(embeddings.hasEmbedding(caseId)).willReturn(false);

        List<?> similar = finder.findSimilar(caseId, 5);

        assertThat(similar).isEmpty();
        verify(embeddings, never()).findNearest(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
