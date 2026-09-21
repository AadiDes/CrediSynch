package com.credisynch.api.decision;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.persistence.ApplicationRepository;
import com.credisynch.api.persistence.GraphRecords.EntityLinkRow;
import com.credisynch.api.persistence.RingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Pipeline stage: full ring clustering, off the decision hot path (docs/architecture.md - "batch
 * jobs ... rings"). {@link GraphLinkageService} already floors a single decision on direct
 * shared-identity counts; this stage does the more expensive thing that stage can't afford
 * synchronously - the full connected-component over every application the new one is transitively
 * linked to - and persists it to {@code rings}/{@code ring_members} so the analyst console can show
 * ring size and detection algorithm (docs/FINDINGS.md finding d).
 *
 * <p>Must be triggered only after the submitting transaction has committed (see
 * {@link DecisionController}), otherwise the just-inserted entity_links row may not yet be visible
 * to the recursive query this runs on a separate thread.
 */
@Service
public class RingDetectionService {

    private static final Logger log = LoggerFactory.getLogger(RingDetectionService.class);

    private final ApplicationRepository applications;
    private final RingRepository rings;
    private final RingDetectionClient client;
    private final int minRingSize;

    public RingDetectionService(ApplicationRepository applications, RingRepository rings,
                                RingDetectionClient client, AppProperties properties) {
        this.applications = applications;
        this.rings = rings;
        this.client = client;
        // Matches GraphLinkageService's own floor: 3+ *other* applications sharing an identity is
        // treated as ring-sized (app.graph.review-linked-threshold) - the cluster is one bigger
        // than that because it also counts the applicant itself.
        this.minRingSize = properties.graph().reviewLinkedThreshold() + 1;
    }

    @Async
    public void detectAsync(UUID applicationId) {
        try {
            List<UUID> candidateIds = applications.findConnectedApplicationIds(applicationId);
            if (candidateIds.size() < minRingSize) {
                return;
            }
            List<EntityLinkRow> links = applications.findEntityLinks(candidateIds);
            client.detect(links, minRingSize).ifPresent(this::persist);
        } catch (Exception e) {
            log.warn("Ring detection failed for application {}: {}", applicationId, e.getMessage());
        }
    }

    private void persist(RingDetectionClient.RingDetectionResponseBody response) {
        for (RingDetectionClient.RingClusterBody cluster : response.clusters()) {
            if (cluster.size() < minRingSize) {
                continue;
            }
            List<UUID> memberIds = cluster.members().stream().map(UUID::fromString).toList();
            String summary = "%d applications linked by shared identifiers (%s)"
                    .formatted(cluster.size(), response.algorithm());

            Optional<UUID> existingRing = rings.findExistingRingId(memberIds);
            UUID ringId = existingRing.orElseGet(UUID::randomUUID);
            if (existingRing.isPresent()) {
                rings.refreshRing(ringId, cluster.size(), cluster.density(), summary);
            } else {
                rings.insertRing(ringId, response.algorithm(), cluster.size(), cluster.density(), summary);
            }
            rings.addMembers(ringId, memberIds);
            log.info("Ring {} now has {} members (density {})", ringId, cluster.size(), cluster.density());
        }
    }
}
