package com.credisynch.api.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * ADR 0003: cosine-distance nearest neighbours over {@code case_embeddings}. Nothing currently
 * writes that table - no embedding pipeline is wired in - so {@link #hasEmbedding} is the guard
 * that keeps similar-case lookup a clean no-op until one exists, instead of guessing.
 */
@Repository
public class CaseEmbeddingRepository {

    private final JdbcTemplate jdbc;

    public CaseEmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasEmbedding(UUID caseId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM case_embeddings WHERE case_id = ?",
                Integer.class, caseId);
        return count != null && count > 0;
    }

    public List<UUID> findNearest(UUID caseId, int limit) {
        return jdbc.query("""
                SELECT ce2.case_id
                FROM case_embeddings ce1
                JOIN case_embeddings ce2 ON ce2.case_id != ce1.case_id
                WHERE ce1.case_id = ?
                ORDER BY ce2.embedding <=> ce1.embedding
                LIMIT ?
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                caseId, limit);
    }

    /** Idempotent: safe to call again for the same case (e.g. a retried async job). */
    public void upsert(UUID caseId, float[] embedding) {
        jdbc.update("""
                INSERT INTO case_embeddings (case_id, embedding) VALUES (?, ?::vector)
                ON CONFLICT (case_id) DO UPDATE SET embedding = EXCLUDED.embedding, created_at = now()
                """, caseId, toPgVectorLiteral(embedding));
    }

    private String toPgVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(embedding[i]);
        }
        return literal.append(']').toString();
    }
}
