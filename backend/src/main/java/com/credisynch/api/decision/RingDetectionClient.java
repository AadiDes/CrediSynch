package com.credisynch.api.decision;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.persistence.GraphRecords.EntityLinkRow;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the Python model service's graph endpoint (ADR 0002: NetworkX owns graph algorithms).
 * Off the decision hot path (RingDetectionService runs {@code @Async}), so a failure here never
 * touches a decision - it just leaves a case without a ring badge until the next detection run.
 */
@Component
public class RingDetectionClient {

    private static final Logger log = LoggerFactory.getLogger(RingDetectionClient.class);

    private final RestClient restClient;

    public RingDetectionClient(AppProperties properties) {
        Duration timeout = Duration.ofMillis(properties.ml().timeoutMs());
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.ml().baseUrl())
                .requestFactory(factory)
                .build();
    }

    public record EntityLinkBody(String application_id, String entity_type, String entity_hash) {}

    public record RingDetectionRequestBody(List<EntityLinkBody> entity_links, int min_cluster_size) {}

    public record RingClusterBody(List<String> members, int size, double density) {}

    public record RingDetectionResponseBody(List<RingClusterBody> clusters, String algorithm) {}

    public Optional<RingDetectionResponseBody> detect(List<EntityLinkRow> entityLinks, int minClusterSize) {
        try {
            List<EntityLinkBody> body = entityLinks.stream()
                    .map(l -> new EntityLinkBody(l.applicationId().toString(), l.entityType(), l.entityHash()))
                    .toList();
            RingDetectionResponseBody response = restClient.post()
                    .uri("/graph/rings")
                    .body(new RingDetectionRequestBody(body, minClusterSize))
                    .retrieve()
                    .body(RingDetectionResponseBody.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Ring-detection service unavailable, skipping this detection run: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
