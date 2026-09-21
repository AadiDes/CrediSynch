package com.credisynch.api.persistence;

import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * At-least-once delivery is the norm for client retries and partner integrations, so the same
 * submission can arrive twice. The key is claimed atomically; a replay returns the stored response
 * instead of creating a second application (a duplicate submission is also a fraud signal worth
 * recording, which phase 3 uses).
 */
@Repository
public class IdempotencyRepository {

    public record Stored(String requestDigest, String responseBody) {}

    private final JdbcTemplate jdbc;

    public IdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true when this call claimed the key, false when it already existed. */
    public boolean tryClaim(String key, String requestDigest) {
        try {
            int inserted = jdbc.update("""
                    INSERT INTO idempotency_keys (idempotency_key, request_digest)
                    VALUES (?, ?)
                    ON CONFLICT (idempotency_key) DO NOTHING
                    """, key, requestDigest);
            return inserted == 1;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public Optional<Stored> find(String key) {
        return jdbc.query("SELECT request_digest, response_body FROM idempotency_keys WHERE idempotency_key = ?",
                rs -> rs.next()
                        ? Optional.of(new Stored(rs.getString(1), rs.getString(2)))
                        : Optional.empty(),
                key);
    }

    public void storeResponse(String key, java.util.UUID applicationId, String responseJson) {
        jdbc.update("UPDATE idempotency_keys SET application_id = ?, response_body = ?::jsonb WHERE idempotency_key = ?",
                applicationId, responseJson, key);
    }
}
