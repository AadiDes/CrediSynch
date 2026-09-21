package com.credisynch.api.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CaseRepository {

    private final JdbcTemplate jdbc;

    public CaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> findLatestCaseForApplication(UUID applicationId) {
        return jdbc.query("SELECT id FROM cases WHERE application_id = ? ORDER BY opened_at DESC LIMIT 1",
                rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(),
                applicationId);
    }

    /** The fast feedback signal: a customer confirmation becomes a label within minutes, not weeks. */
    public void insertLabel(UUID caseId, String label, String source, String labelledBy, String note) {
        jdbc.update("""
                INSERT INTO case_labels (id, case_id, label, label_source, labelled_by, note)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), caseId, label, source, labelledBy, note);
    }
}
