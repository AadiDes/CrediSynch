package com.credisynch.api.decision;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.persistence.ApplicationRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Pipeline stage 3: shared devices, phones, emails, addresses and bank accounts are the cheapest
 * fraud-ring signal there is, and the fastest to check - a single indexed query against
 * {@code entity_links} (ADR 0006), so it stays on the synchronous decision path.
 *
 * Full ring clustering (connected components / Louvain, the {@code rings} table) is expensive and
 * runs off this path; this stage only needs the count of other applications a fresh submission is
 * already touching.
 */
@Component
public class GraphLinkageService {

    private final ApplicationRepository applications;
    private final AppProperties.Graph config;

    public GraphLinkageService(ApplicationRepository applications, AppProperties properties) {
        this.applications = applications;
        this.config = properties.graph();
    }

    /** @param linkedApplicationCount other applications sharing at least one identity with this one */
    public record Assessment(int linkedApplicationCount, double risk, DecisionAction floor) {}

    public Assessment assess(List<String> entityHashes) {
        List<String> hashes = entityHashes.stream().filter(Objects::nonNull).toList();
        int linked = applications.countLinkedApplications(hashes, config.linkWindowHours());
        return new Assessment(linked, riskOf(linked), floorOf(linked));
    }

    /**
     * Diminishing returns, no arbitrary scale to tune: 0 shared applications is 0 risk, 1 is 0.5,
     * 2 is 0.667, climbing towards but never reaching 1 as the shared cluster grows.
     */
    private double riskOf(int linked) {
        return linked == 0 ? 0.0 : 1.0 - 1.0 / (1.0 + linked);
    }

    private DecisionAction floorOf(int linked) {
        if (linked >= config.reviewLinkedThreshold()) {
            return DecisionAction.REVIEW;
        }
        if (linked >= config.stepUpLinkedThreshold()) {
            return DecisionAction.STEP_UP;
        }
        return null;
    }
}
