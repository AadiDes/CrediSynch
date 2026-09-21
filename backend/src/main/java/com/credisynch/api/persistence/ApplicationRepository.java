package com.credisynch.api.persistence;

import com.credisynch.api.persistence.DecisionRecords.ApplicationRow;
import com.credisynch.api.persistence.GraphRecords.EntityLinkRow;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationRepository {

    private final JdbcTemplate jdbc;

    public ApplicationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(ApplicationRow row) {
        jdbc.update("""
                INSERT INTO applications
                    (id, external_ref, channel, partner_id, applicant_name_hash, features, raw_payload)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """,
                row.id(), row.externalRef(), row.channel(), row.partnerId(),
                row.applicantNameHash(), row.featuresJson(), row.rawPayloadJson());
    }

    /** How many applications have shared any of these entity digests in the given window. */
    public int countLinkedApplications(java.util.List<String> entityHashes, int withinHours) {
        if (entityHashes.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(entityHashes.size(), "?"));
        Object[] args = new Object[entityHashes.size() + 1];
        for (int i = 0; i < entityHashes.size(); i++) {
            args[i] = entityHashes.get(i);
        }
        args[entityHashes.size()] = withinHours;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT application_id) FROM entity_links
                WHERE entity_hash IN (%s)
                  AND observed_at > now() - make_interval(hours => ?)
                """.formatted(placeholders), Integer.class, args);
        return count == null ? 0 : count;
    }

    public void insertEntityLink(UUID applicationId, String entityType, String entityHash) {
        if (entityHash == null) {
            return;
        }
        jdbc.update("INSERT INTO entity_links (application_id, entity_type, entity_hash) VALUES (?, ?, ?)",
                applicationId, entityType, entityHash);
    }

    /** Other applications sharing at least one entity with this one - the case detail's evidence trail. */
    public java.util.List<UUID> findLinkedApplicationIds(UUID applicationId, int withinHours) {
        return jdbc.query("""
                SELECT DISTINCT el2.application_id
                FROM entity_links el1
                JOIN entity_links el2 ON el2.entity_hash = el1.entity_hash AND el2.application_id != el1.application_id
                WHERE el1.application_id = ? AND el2.observed_at > now() - make_interval(hours => ?)
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                applicationId, withinHours);
    }

    /**
     * The full transitive closure of applications reachable from {@code applicationId} by following
     * shared entity hashes (device/phone/email/address/bank account/IP), any number of hops. This is
     * exactly the connected component the ring-detection graph algorithm (ml-service) will cluster -
     * the recursive query finds the candidate neighbourhood; NetworkX (ADR 0002) does the clustering.
     */
    public List<UUID> findConnectedApplicationIds(UUID applicationId) {
        return jdbc.query("""
                WITH RECURSIVE cluster_apps(application_id) AS (
                    SELECT CAST(? AS uuid)
                    UNION
                    SELECT el2.application_id
                    FROM cluster_apps ca
                    JOIN entity_links el1 ON el1.application_id = ca.application_id
                    JOIN entity_links el2 ON el2.entity_hash = el1.entity_hash
                )
                SELECT application_id FROM cluster_apps
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                applicationId);
    }

    /** Raw entity-link evidence for a set of applications - the edge list the graph algorithm clusters. */
    public List<EntityLinkRow> findEntityLinks(List<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(applicationIds.size(), "?"));
        return jdbc.query("""
                SELECT application_id, entity_type, entity_hash FROM entity_links
                WHERE application_id IN (%s)
                """.formatted(placeholders),
                (rs, rowNum) -> new EntityLinkRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                applicationIds.toArray());
    }
}
