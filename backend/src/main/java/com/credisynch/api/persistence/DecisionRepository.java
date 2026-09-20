package com.credisynch.api.persistence;

import com.credisynch.api.decision.DecisionAction;
import com.credisynch.api.persistence.DecisionRecords.DecisionRow;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Decisions are append-only: there is deliberately no update or delete here. */
@Repository
public class DecisionRepository {

    private final JdbcTemplate jdbc;

    public DecisionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(DecisionRow row) {
        jdbc.update("""
                INSERT INTO decisions
                    (id, application_id, action, fraud_probability, graph_risk, novelty_score,
                     rules_fired, reason_codes, model_version, policy_version, latency_ms, degraded_mode)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                """,
                row.id(), row.applicationId(), row.action().name(), row.fraudProbability(),
                row.graphRisk(), row.noveltyScore(), row.rulesFiredJson(), row.reasonCodesJson(),
                row.modelVersion(), row.policyVersion(), row.latencyMs(), row.degradedMode());
    }

    public Optional<UUID> findDecisionIdByApplication(UUID applicationId) {
        return jdbc.query("SELECT id FROM decisions WHERE application_id = ? ORDER BY decided_at LIMIT 1",
                        rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(),
                        applicationId);
    }

    public void openCase(UUID caseId, UUID applicationId, UUID decisionId, DecisionAction action) {
        int priority = switch (action) {
            case DECLINE, REVIEW -> 1;
            case APPROVE_RESTRICTED -> 2;
            default -> 3;
        };
        jdbc.update("INSERT INTO cases (id, application_id, decision_id, priority) VALUES (?, ?, ?, ?)",
                caseId, applicationId, decisionId, priority);
    }
}
