package com.credisynch.api.persistence;

import com.credisynch.api.persistence.RestrictedRecords.TransactionRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepository {

    private final JdbcTemplate jdbc;

    public TransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(TransactionRow row) {
        jdbc.update("""
                INSERT INTO transactions
                    (id, card_account_id, amount_minor, raw_descriptor, matched_partner_id,
                     match_score, match_method, outcome, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.id(), row.cardAccountId(), row.amountMinor(), row.rawDescriptor(), row.matchedPartnerId(),
                row.matchScore(), row.matchMethod(), row.outcome(), row.reason());
    }

    /** Approved transactions on this card in the rolling window, for the velocity cap. */
    public int countApprovedSince(UUID cardAccountId, Instant since) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM transactions
                WHERE card_account_id = ? AND outcome = 'APPROVED' AND occurred_at > ?
                """, Integer.class, cardAccountId, Timestamp.from(since));
        return count == null ? 0 : count;
    }
}
