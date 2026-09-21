package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.credisynch.api.cases.CaseDetailResponse;
import com.credisynch.api.cases.CaseNarrativeGenerator;
import com.credisynch.api.cases.CasePageResponse;
import com.credisynch.api.cases.CaseService;
import com.credisynch.api.cases.LabelRequest;
import com.credisynch.api.cases.SimilarCaseFinder;
import com.credisynch.api.config.AppProperties;
import com.credisynch.api.persistence.ApplicationRepository;
import com.credisynch.api.persistence.CaseRecords.CaseDetailRow;
import com.credisynch.api.persistence.CaseRecords.CaseSummaryRow;
import com.credisynch.api.persistence.CaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CaseServiceTest {

    private final CaseRepository cases = mock(CaseRepository.class);
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final SimilarCaseFinder similarCaseFinder = mock(SimilarCaseFinder.class);
    private final CaseNarrativeGenerator narrativeGenerator = mock(CaseNarrativeGenerator.class);

    private CaseService service() {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ml("http://localhost:8000", 400),
                new AppProperties.Hashing("test-key"),
                new AppProperties.Policy("policy-test", 1500.0, 3.0, 0.8, 5.0, 0.1, 150.0),
                new AppProperties.Graph(720, 1, 3),
                new AppProperties.Restricted(50000, 3, 90, 0.45));
        return new CaseService(cases, applications, similarCaseFinder, narrativeGenerator, new ObjectMapper(),
                properties);
    }

    private CaseSummaryRow summaryRow(UUID caseId) {
        return new CaseSummaryRow(caseId, UUID.randomUUID(), "OPEN", 1, "REVIEW", 0.6, null, Instant.now());
    }

    @Test
    @DisplayName("a full page (== limit) carries a next cursor; a short page does not")
    void nextCursorOnlyWhenPageIsFull() {
        given(cases.findPage(null, null, 2)).willReturn(List.of(summaryRow(UUID.randomUUID()), summaryRow(UUID.randomUUID())));
        given(cases.findPage(null, null, 3)).willReturn(List.of(summaryRow(UUID.randomUUID())));

        CasePageResponse fullPage = service().list(null, null, 2);
        CasePageResponse shortPage = service().list(null, null, 3);

        assertThat(fullPage.nextCursor()).isNotNull();
        assertThat(shortPage.nextCursor()).isNull();
    }

    @Test
    @DisplayName("an unknown case detail is empty, not an error")
    void unknownCaseDetailIsEmpty() {
        UUID caseId = UUID.randomUUID();
        given(cases.findDetail(caseId)).willReturn(Optional.empty());

        Optional<CaseDetailResponse> detail = service().detail(caseId);

        assertThat(detail).isEmpty();
    }

    @Test
    @DisplayName("case detail carries reason codes, linked applications, and a null brief with no LLM provider")
    void detailAssemblesEvidenceWithoutABrief() {
        UUID caseId = UUID.randomUUID();
        UUID linkedAppId = UUID.randomUUID();
        CaseSummaryRow summary = summaryRow(caseId);
        String reasonCodesJson = "[{\"code\":\"VELOCITY_6H\",\"feature\":\"velocity_6h\",\"contribution\":0.5,\"direction\":\"INCREASES_RISK\"}]";
        given(cases.findDetail(caseId)).willReturn(Optional.of(new CaseDetailRow(summary, UUID.randomUUID(), reasonCodesJson)));
        given(applications.findLinkedApplicationIds(summary.applicationId(), 720)).willReturn(List.of(linkedAppId));
        given(similarCaseFinder.findSimilar(caseId, 5)).willReturn(List.of());
        given(narrativeGenerator.generate(any())).willReturn(Optional.empty());

        CaseDetailResponse detail = service().detail(caseId).orElseThrow();

        assertThat(detail.reasonCodes()).hasSize(1);
        assertThat(detail.reasonCodes().get(0).feature()).isEqualTo("velocity_6h");
        assertThat(detail.linkedApplications()).containsExactly(linkedAppId);
        assertThat(detail.brief()).isNull();
        assertThat(detail.briefModel()).isNull();
    }

    @Test
    @DisplayName("labelling an unknown case reports not-found and never writes")
    void labellingUnknownCaseIsNotFound() {
        UUID caseId = UUID.randomUUID();
        given(cases.exists(caseId)).willReturn(false);

        boolean recorded = service().label(caseId, new LabelRequest("FRAUD", "note"), "analyst-1");

        assertThat(recorded).isFalse();
        verify(cases, never()).insertLabel(any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("labelling a real case records it with source ANALYST and the caller as labelled_by")
    void labellingRecordsAsAnalyst() {
        UUID caseId = UUID.randomUUID();
        given(cases.exists(caseId)).willReturn(true);

        boolean recorded = service().label(caseId, new LabelRequest("LEGITIMATE", null), "analyst-1");

        assertThat(recorded).isTrue();
        verify(cases).insertLabel(caseId, "LEGITIMATE", "ANALYST", "analyst-1", null);
    }
}
