package com.credisynch.api.persistence;

import com.credisynch.api.persistence.RestrictedRecords.MerchantMatch;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Resolves a messy card descriptor to a merchant partner (ADR 0007). */
@Repository
public class MerchantRepository {

    private final JdbcTemplate jdbc;

    public MerchantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findExactMatch(String normalizedDescriptor) {
        return jdbc.query("SELECT partner_id FROM merchants WHERE upper(descriptor_pattern) = ? LIMIT 1",
                rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(),
                normalizedDescriptor);
    }

    /** Best trigram match across the whole catalog; the caller decides whether the score is confident enough. */
    public Optional<MerchantMatch> findBestTrigramMatch(String normalizedDescriptor) {
        return jdbc.query("""
                SELECT partner_id, similarity(descriptor_pattern, ?) AS score
                FROM merchants
                ORDER BY score DESC
                LIMIT 1
                """,
                rs -> rs.next() ? Optional.of(new MerchantMatch(rs.getString(1), rs.getDouble(2))) : Optional.empty(),
                normalizedDescriptor);
    }

    /** Best embedding cosine-similarity match; only meaningful once the catalog is backfilled. */
    public Optional<MerchantMatch> findBestVectorMatch(String embeddingLiteral) {
        return jdbc.query("""
                SELECT partner_id, 1 - (descriptor_embedding <=> ?::vector) AS score
                FROM merchants
                WHERE descriptor_embedding IS NOT NULL
                ORDER BY descriptor_embedding <=> ?::vector
                LIMIT 1
                """,
                rs -> rs.next() ? Optional.of(new MerchantMatch(rs.getString(1), rs.getDouble(2))) : Optional.empty(),
                embeddingLiteral, embeddingLiteral);
    }

    public List<String> findPartnerIdsMissingEmbedding() {
        return jdbc.queryForList("SELECT partner_id FROM merchants WHERE descriptor_embedding IS NULL", String.class);
    }

    public String findDescriptorPattern(String partnerId) {
        return jdbc.queryForObject("SELECT descriptor_pattern FROM merchants WHERE partner_id = ?",
                String.class, partnerId);
    }

    public void updateEmbedding(String partnerId, String embeddingLiteral) {
        jdbc.update("UPDATE merchants SET descriptor_embedding = ?::vector WHERE partner_id = ?",
                embeddingLiteral, partnerId);
    }
}
