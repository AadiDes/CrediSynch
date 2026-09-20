package com.credisynch.api.persistence;

import com.credisynch.api.persistence.DecisionRecords.ApplicationRow;
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
}
