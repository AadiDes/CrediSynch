package com.credisynch.api.cases;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.persistence.ApplicationRepository;
import com.credisynch.api.persistence.CaseRecords.CaseDetailRow;
import com.credisynch.api.persistence.CaseRecords.CaseSummaryRow;
import com.credisynch.api.persistence.CaseRecords.RingRow;
import com.credisynch.api.persistence.CaseRepository;
import com.credisynch.api.scoring.ReasonCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The analyst case queue: list, detail (reason codes, graph evidence, brief if one exists), labels. */
@Service
public class CaseService {

    private static final int SIMILAR_CASES_LIMIT = 5;

    private final CaseRepository cases;
    private final ApplicationRepository applications;
    private final SimilarCaseFinder similarCaseFinder;
    private final CaseNarrativeGenerator narrativeGenerator;
    private final ObjectMapper objectMapper;
    private final int linkWindowHours;

    public CaseService(CaseRepository cases, ApplicationRepository applications,
                       SimilarCaseFinder similarCaseFinder, CaseNarrativeGenerator narrativeGenerator,
                       ObjectMapper objectMapper, AppProperties properties) {
        this.cases = cases;
        this.applications = applications;
        this.similarCaseFinder = similarCaseFinder;
        this.narrativeGenerator = narrativeGenerator;
        this.objectMapper = objectMapper;
        this.linkWindowHours = properties.graph().linkWindowHours();
    }

    public CasePageResponse list(String status, String cursor, int limit) {
        Instant cursorInstant = cursor == null ? null : Instant.parse(cursor);
        List<CaseSummaryRow> page = cases.findPage(status, cursorInstant, limit);
        List<CaseSummaryResponse> items = page.stream().map(this::toSummary).toList();
        String nextCursor = page.size() == limit ? page.get(page.size() - 1).openedAt().toString() : null;
        return new CasePageResponse(items, nextCursor);
    }

    public Optional<CaseDetailResponse> detail(UUID caseId) {
        return cases.findDetail(caseId).map(this::toDetail);
    }

    /** @return false when no such case exists. */
    public boolean label(UUID caseId, LabelRequest request, String actor) {
        if (!cases.exists(caseId)) {
            return false;
        }
        cases.insertLabel(caseId, request.label(), "ANALYST", actor, request.note());
        return true;
    }

    public QueueSummaryResponse queueSummary() {
        var summary = cases.queueSummary();
        return new QueueSummaryResponse(summary.openCount(), summary.inReviewCount(), summary.closedCount(),
                summary.byPriority());
    }

    private CaseSummaryResponse toSummary(CaseSummaryRow row) {
        return new CaseSummaryResponse(row.caseId(), row.applicationId(), row.status(), row.priority(),
                row.action(), row.fraudProbability(), row.ringId(), row.openedAt());
    }

    private CaseDetailResponse toDetail(CaseDetailRow row) {
        CaseSummaryRow s = row.summary();
        List<ReasonCode> reasonCodes = parseReasonCodes(row.reasonCodesJson());
        List<UUID> linkedApplications = applications.findLinkedApplicationIds(s.applicationId(), linkWindowHours);
        RingResponse ring = s.ringId() == null ? null : cases.findRing(s.ringId()).map(this::toRing).orElse(null);
        List<CaseSummaryResponse> similarCases = similarCaseFinder.findSimilar(s.caseId(), SIMILAR_CASES_LIMIT)
                .stream().map(this::toSummary).toList();

        String brief = row.brief();
        String briefModel = row.briefModel();
        if (brief == null) {
            CaseNarrativeGenerator.Context narrativeContext = new CaseNarrativeGenerator.Context(
                    s.caseId(), s.action(), s.fraudProbability(),
                    reasonCodes.stream()
                            .map(rc -> new CaseNarrativeGenerator.ReasonCodeView(rc.code(), rc.feature(),
                                    rc.contribution(), rc.direction()))
                            .toList(),
                    linkedApplications);
            Optional<CaseNarrativeGenerator.Narrative> narrative = narrativeGenerator.generate(narrativeContext);
            if (narrative.isPresent()) {
                brief = narrative.get().brief();
                briefModel = narrative.get().model();
                cases.saveBrief(s.caseId(), brief, briefModel);
            }
        }

        return new CaseDetailResponse(s.caseId(), s.applicationId(), s.status(), s.priority(), s.action(),
                s.fraudProbability(), s.ringId(), s.openedAt(), reasonCodes, linkedApplications, ring,
                brief, briefModel, similarCases);
    }

    private RingResponse toRing(RingRow row) {
        return new RingResponse(row.ringId(), row.algorithm(), row.size(), row.density(), List.of());
    }

    private List<ReasonCode> parseReasonCodes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ReasonCode>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
