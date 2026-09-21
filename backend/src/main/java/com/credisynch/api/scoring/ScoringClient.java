package com.credisynch.api.scoring;

import com.credisynch.api.config.AppProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the Python model service on the hot path.
 * Explicit failure path: a timeout or error never fails the request; it degrades the decision,
 * and the policy engine then chooses a conservative action.
 */
@Component
public class ScoringClient {

    private static final Logger log = LoggerFactory.getLogger(ScoringClient.class);

    private final RestClient restClient;

    public ScoringClient(AppProperties properties) {
        Duration timeout = Duration.ofMillis(properties.ml().timeoutMs());
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.ml().baseUrl())
                .requestFactory(factory)
                .build();
    }

    public record ScoreRequestBody(String application_id, Map<String, Object> features) {}

    public record ScoreResponseBody(
            String application_id,
            String model_version,
            Double fraud_probability,
            Double novelty_score,
            List<ReasonCodeBody> reason_codes,
            Double scored_in_ms,
            Boolean placeholder) {}

    public record ReasonCodeBody(String code, String feature, Double contribution, String direction) {}

    public ScoreResult score(String applicationId, Map<String, Object> features) {
        try {
            ScoreResponseBody body = restClient.post()
                    .uri("/score")
                    .body(new ScoreRequestBody(applicationId, features))
                    .retrieve()
                    .body(ScoreResponseBody.class);
            if (body == null || body.fraud_probability() == null) {
                return ScoreResult.degradedResult();
            }
            List<ReasonCode> reasons = body.reason_codes() == null ? List.of()
                    : body.reason_codes().stream()
                        .map(r -> new ReasonCode(r.code(), r.feature(),
                                r.contribution() == null ? 0.0 : r.contribution(), r.direction()))
                        .toList();
            return new ScoreResult(body.fraud_probability(),
                    body.novelty_score() == null ? 0.0 : body.novelty_score(),
                    reasons, body.model_version(), false);
        } catch (Exception e) {
            log.warn("Model service unavailable, degrading decision: {}", e.getMessage());
            return ScoreResult.degradedResult();
        }
    }
}
