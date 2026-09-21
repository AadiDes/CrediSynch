package com.credisynch.api.persistence;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Append-only audit trail: who did what, to which resource, under which correlation id. */
@Repository
public class AuditRepository {

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String actor, String roles, String action, String resourceType, String resourceId,
                       String detailJson) {
        jdbc.update("""
                INSERT INTO audit_log (actor, actor_roles, action, resource_type, resource_id, correlation_id, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                actor, roles, action, resourceType, resourceId,
                MDC.get("correlationId"), detailJson == null ? "{}" : detailJson);
    }
}
