package com.credisynch.api.persistence;

import com.credisynch.api.persistence.RestrictedRecords.MerchantMatch;
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
}
