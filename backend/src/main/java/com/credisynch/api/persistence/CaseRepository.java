package com.credisynch.api.persistence;

import com.credisynch.api.persistence.CaseRecords.CaseDetailRow;
import com.credisynch.api.persistence.CaseRecords.CaseSummaryRow;
import com.credisynch.api.persistence.CaseRecords.QueueSummary;
import com.credisynch.api.persistence.CaseRecords.RingRow;
import com.credisynch.api.persistence.CaseRecords.SharedEntityRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CaseRepository {

    /** Most recently detected ring an application belongs to, if any - ring clustering runs off the hot path. */
    private static final String LATEST_RING_SUBQUERY = """
            (SELECT rm.ring_id FROM ring_members rm JOIN rings r ON r.id = rm.ring_id
             WHERE rm.application_id = c.application_id ORDER BY r.detected_at DESC LIMIT 1)
            """;

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

    public boolean exists(UUID caseId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cases WHERE id = ?", Integer.class, caseId);
        return count != null && count > 0;
    }

    /**
     * Keyset pagination on (priority, opened_at): {@code cursor} is the opened_at of the last row
     * of the previous page (null for the first page). Simple and index-friendly; ties at the exact
     * same timestamp across a page boundary are the one edge case it does not resolve perfectly,
     * acceptable at this scale.
     */
    public List<CaseSummaryRow> findPage(String status, Instant cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.application_id, c.status, c.priority, d.action, d.fraud_probability,
                       %s AS ring_id, c.opened_at
                FROM cases c
                JOIN decisions d ON d.id = c.decision_id
                WHERE 1 = 1
                """.formatted(LATEST_RING_SUBQUERY));
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" AND c.status = ?");
            args.add(status);
        }
        if (cursor != null) {
            sql.append(" AND c.opened_at < ?");
            args.add(Timestamp.from(cursor));
        }
        sql.append(" ORDER BY c.priority ASC, c.opened_at DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, rowNum) -> mapSummary(rs), args.toArray());
    }

    public Optional<CaseSummaryRow> findSummary(UUID caseId) {
        String sql = """
                SELECT c.id, c.application_id, c.status, c.priority, d.action, d.fraud_probability,
                       %s AS ring_id, c.opened_at
                FROM cases c
                JOIN decisions d ON d.id = c.decision_id
                WHERE c.id = ?
                """.formatted(LATEST_RING_SUBQUERY);
        return jdbc.query(sql, rs -> rs.next() ? Optional.of(mapSummary(rs)) : Optional.empty(), caseId);
    }

    public Optional<CaseDetailRow> findDetail(UUID caseId) {
        String sql = """
                SELECT c.id, c.application_id, c.status, c.priority, d.action, d.fraud_probability,
                       %s AS ring_id, c.opened_at, c.decision_id, d.reason_codes, c.brief, c.brief_model
                FROM cases c
                JOIN decisions d ON d.id = c.decision_id
                WHERE c.id = ?
                """.formatted(LATEST_RING_SUBQUERY);
        return jdbc.query(sql, rs -> rs.next()
                        ? Optional.of(new CaseDetailRow(mapSummary(rs), rs.getObject(9, UUID.class), rs.getString(10),
                                rs.getString(11), rs.getString(12)))
                        : Optional.empty(),
                caseId);
    }

    /** Persists a brief the first time it's generated, so every later case-detail view is served from
     * the database instead of re-calling (and re-billing) the LLM on each open. */
    public void saveBrief(UUID caseId, String brief, String model) {
        jdbc.update("UPDATE cases SET brief = ?, brief_model = ? WHERE id = ?", brief, model, caseId);
    }

    public Optional<RingRow> findRing(UUID ringId) {
        return jdbc.query("SELECT id, algorithm, size, density FROM rings WHERE id = ?",
                rs -> rs.next()
                        ? Optional.of(new RingRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3),
                                nullableDouble(rs, 4)))
                        : Optional.empty(),
                ringId);
    }

    /** Which entity types are actually shared (not merely present) across a ring's members. */
    public List<SharedEntityRow> findSharedEntities(UUID ringId) {
        return jdbc.query("""
                SELECT el.entity_type, COUNT(DISTINCT el.application_id) AS app_count
                FROM entity_links el
                WHERE el.application_id IN (SELECT application_id FROM ring_members WHERE ring_id = ?)
                  AND el.entity_hash IN (
                      SELECT entity_hash FROM entity_links
                      WHERE application_id IN (SELECT application_id FROM ring_members WHERE ring_id = ?)
                      GROUP BY entity_hash
                      HAVING COUNT(DISTINCT application_id) > 1
                  )
                GROUP BY el.entity_type
                ORDER BY el.entity_type
                """,
                (rs, rowNum) -> new SharedEntityRow(rs.getString(1), rs.getInt(2)),
                ringId, ringId);
    }

    public QueueSummary queueSummary() {
        List<Map.Entry<String, Integer>> statusCounts = jdbc.query(
                "SELECT status, COUNT(*) FROM cases GROUP BY status",
                (rs, rowNum) -> Map.entry(rs.getString(1), rs.getInt(2)));
        Map<String, Integer> byStatus = new HashMap<>();
        statusCounts.forEach(e -> byStatus.merge(e.getKey(), e.getValue(), Integer::sum));

        List<Map.Entry<Integer, Integer>> priorityCounts = jdbc.query(
                "SELECT priority, COUNT(*) FROM cases WHERE status = 'OPEN' GROUP BY priority",
                (rs, rowNum) -> Map.entry(rs.getInt(1), rs.getInt(2)));
        Map<Integer, Integer> byPriority = new HashMap<>();
        priorityCounts.forEach(e -> byPriority.merge(e.getKey(), e.getValue(), Integer::sum));

        return new QueueSummary(
                byStatus.getOrDefault("OPEN", 0),
                byStatus.getOrDefault("IN_REVIEW", 0),
                byStatus.getOrDefault("CLOSED", 0),
                byPriority);
    }

    private CaseSummaryRow mapSummary(ResultSet rs) throws SQLException {
        return new CaseSummaryRow(
                rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class),
                rs.getString(3),
                rs.getInt(4),
                rs.getString(5),
                nullableDouble(rs, 6),
                rs.getObject(7, UUID.class),
                rs.getTimestamp(8).toInstant());
    }

    /** pgjdbc's getObject(int, Double.class) rejects NUMERIC columns outright; getDouble + wasNull works. */
    private Double nullableDouble(ResultSet rs, int columnIndex) throws SQLException {
        double value = rs.getDouble(columnIndex);
        return rs.wasNull() ? null : value;
    }
}
