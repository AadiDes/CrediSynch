package com.credisynch.api.decision;

import com.credisynch.api.common.EntityHasher;
import com.credisynch.api.decision.rules.RuleChain;
import com.credisynch.api.persistence.ApplicationRepository;
import com.credisynch.api.persistence.AuditRepository;
import com.credisynch.api.persistence.DecisionRecords.ApplicationRow;
import com.credisynch.api.persistence.DecisionRecords.DecisionRow;
import com.credisynch.api.persistence.DecisionRepository;
import com.credisynch.api.persistence.IdempotencyRepository;
import com.credisynch.api.scoring.ScoreResult;
import com.credisynch.api.scoring.ScoringClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the synchronous stages of the pipeline:
 * gateway hygiene (idempotency) -> rules -> model -> policy -> persistence -> case.
 * Everything expensive (analyst brief, similar cases, ring detection) happens off this path.
 */
@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

    private final RuleChain ruleChain;
    private final ScoringClient scoringClient;
    private final PolicyEngine policyEngine;
    private final ApplicationRepository applications;
    private final DecisionRepository decisions;
    private final IdempotencyRepository idempotency;
    private final AuditRepository audit;
    private final EntityHasher hasher;
    private final ObjectMapper objectMapper;
    private final Timer decisionTimer;
    private final MeterRegistry meterRegistry;

    public DecisionService(RuleChain ruleChain, ScoringClient scoringClient, PolicyEngine policyEngine,
                           ApplicationRepository applications, DecisionRepository decisions,
                           IdempotencyRepository idempotency, AuditRepository audit,
                           EntityHasher hasher, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.ruleChain = ruleChain;
        this.scoringClient = scoringClient;
        this.policyEngine = policyEngine;
        this.applications = applications;
        this.decisions = decisions;
        this.idempotency = idempotency;
        this.audit = audit;
        this.hasher = hasher;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.decisionTimer = Timer.builder("credisynch.decision.latency")
                .description("End-to-end latency of the synchronous decision path")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    public static class IdempotencyConflict extends RuntimeException {
        public IdempotencyConflict(String message) {
            super(message);
        }
    }

    @Transactional
    public DecisionResponse decide(ApplicationRequest request, String idempotencyKey, String actor) {
        String digest = digestOf(request);
        Optional<IdempotencyRepository.Stored> existing = idempotency.find(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), digest, idempotencyKey);
        }
        if (!idempotency.tryClaim(idempotencyKey, digest)) {
            return replay(idempotency.find(idempotencyKey).orElseThrow(), digest, idempotencyKey);
        }

        long started = System.nanoTime();
        UUID applicationId = UUID.randomUUID();

        RuleChain.Result rules = ruleChain.evaluate(request);
        ScoreResult score = scoringClient.score(applicationId.toString(), request.featuresOrEmpty());
        DecisionAction action = policyEngine.decide(score.fraudProbability(), score.degraded(), rules.floor());

        persist(applicationId, request, score, rules, action, started);

        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        decisionTimer.record(java.time.Duration.ofNanos(System.nanoTime() - started));
        meterRegistry.counter("credisynch.decision.actions", "action", action.name()).increment();

        DecisionResponse response = new DecisionResponse(
                applicationId,
                decisions.findDecisionIdByApplication(applicationId).orElse(null),
                action,
                policyEngine.customerMessage(action),
                score.degraded() ? null : score.fraudProbability(),
                null,
                score.degraded() ? null : score.noveltyScore(),
                score.reasonCodes(),
                rules.firedIds(),
                score.modelVersion(),
                policyEngine.version(),
                score.degraded(),
                latencyMs,
                Instant.now());

        idempotency.storeResponse(idempotencyKey, applicationId, toJson(response));
        audit.record(actor, null, "DECISION_ISSUED", "application", applicationId.toString(),
                toJson(java.util.Map.of("action", action.name(), "degraded", score.degraded())));
        log.debug("Decided {} for application {} in {} ms", action, applicationId, latencyMs);
        return response;
    }

    private void persist(UUID applicationId, ApplicationRequest request, ScoreResult score,
                         RuleChain.Result rules, DecisionAction action, long startedNanos) {
        applications.insert(new ApplicationRow(
                applicationId,
                request.externalRef(),
                request.channel(),
                request.partnerId(),
                hasher.hash("NAME", request.applicant().fullName()),
                toJson(request.featuresOrEmpty()),
                toJson(request)));

        // Linkage is stored as keyed digests only: enough to spot sharing, useless if leaked.
        applications.insertEntityLink(applicationId, "DEVICE",
                hasher.hash("DEVICE", request.device().deviceFingerprint()));
        applications.insertEntityLink(applicationId, "PHONE", hasher.hash("PHONE", request.applicant().phone()));
        applications.insertEntityLink(applicationId, "EMAIL", hasher.hash("EMAIL", request.applicant().email()));
        applications.insertEntityLink(applicationId, "ADDRESS",
                hasher.hash("ADDRESS", request.applicant().addressLine()));
        applications.insertEntityLink(applicationId, "BANK_ACCOUNT",
                hasher.hash("BANK_ACCOUNT", request.applicant().bankAccountRef()));
        if (request.device().ipAddress() != null) {
            applications.insertEntityLink(applicationId, "IP", hasher.hash("IP", request.device().ipAddress()));
        }

        UUID decisionId = UUID.randomUUID();
        decisions.insert(new DecisionRow(
                decisionId,
                applicationId,
                action,
                score.degraded() ? null : score.fraudProbability(),
                null,
                score.degraded() ? null : score.noveltyScore(),
                toJson(rules.firedIds()),
                toJson(score.reasonCodes()),
                score.modelVersion(),
                policyEngine.version(),
                (System.nanoTime() - startedNanos) / 1_000_000,
                score.degraded()));

        if (action.opensCase()) {
            decisions.openCase(UUID.randomUUID(), applicationId, decisionId, action);
        }
    }

    private DecisionResponse replay(IdempotencyRepository.Stored stored, String digest, String key) {
        if (!stored.requestDigest().equals(digest)) {
            throw new IdempotencyConflict("Idempotency key " + key + " was already used with a different payload");
        }
        meterRegistry.counter("credisynch.decision.idempotent_replays").increment();
        try {
            return objectMapper.readValue(stored.responseBody(), DecisionResponse.class);
        } catch (JsonProcessingException | NullPointerException e) {
            throw new IllegalStateException("Stored idempotent response could not be read", e);
        }
    }

    private String digestOf(ApplicationRequest request) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to digest request", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** Exposed for the platform endpoint so the thresholds in the deck can be shown live. */
    public List<String> describeBands() {
        PolicyEngine.Bands bands = policyEngine.bands();
        List<String> description = new ArrayList<>();
        description.add("APPROVE: p < %.5f".formatted(bands.stepUp()));
        description.add("STEP_UP: %.5f <= p < %.5f".formatted(bands.stepUp(), bands.restricted()));
        description.add("APPROVE_RESTRICTED: %.5f <= p < %.5f".formatted(bands.restricted(), bands.review()));
        description.add("REVIEW: p >= %.5f".formatted(bands.review()));
        return description;
    }
}
