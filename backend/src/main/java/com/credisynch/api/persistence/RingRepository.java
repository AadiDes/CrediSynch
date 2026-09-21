package com.credisynch.api.persistence;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists ring-detection results (off the decision hot path - RingDetectionService). */
@Repository
public class RingRepository {

    private final JdbcTemplate jdbc;

    public RingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The most recently detected ring that already covers any of these applications, if one exists. */
    public Optional<UUID> findExistingRingId(List<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Optional.empty();
        }
        String placeholders = String.join(",", Collections.nCopies(applicationIds.size(), "?"));
        return jdbc.query("""
                SELECT rm.ring_id FROM ring_members rm
                JOIN rings r ON r.id = rm.ring_id
                WHERE rm.application_id IN (%s)
                ORDER BY r.detected_at DESC
                LIMIT 1
                """.formatted(placeholders),
                rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(),
                applicationIds.toArray());
    }

    public void insertRing(UUID ringId, String algorithm, int size, double density, String summary) {
        jdbc.update("""
                INSERT INTO rings (id, algorithm, size, density, summary) VALUES (?, ?, ?, ?, ?)
                """, ringId, algorithm, size, density, summary);
    }

    /** A ring grows as new members are detected; re-detected_at marks it as the freshest clustering. */
    public void refreshRing(UUID ringId, int size, double density, String summary) {
        jdbc.update("""
                UPDATE rings SET size = ?, density = ?, summary = ?, detected_at = now() WHERE id = ?
                """, size, density, summary, ringId);
    }

    public void addMembers(UUID ringId, List<UUID> applicationIds) {
        for (UUID applicationId : applicationIds) {
            jdbc.update("""
                    INSERT INTO ring_members (ring_id, application_id) VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """, ringId, applicationId);
        }
    }
}
