package com.credisynch.api.persistence;

import com.credisynch.api.persistence.RestrictedRecords.ConfirmationContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** "Was this you?" - customer answers on a CONFIRM_PENDING transaction (ADR 0007). */
@Repository
public class ConfirmationRepository {

    private final JdbcTemplate jdbc;

    public ConfirmationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID transactionId) {
        jdbc.update("INSERT INTO customer_confirmations (id, transaction_id) VALUES (?, ?)", id, transactionId);
    }

    public Optional<ConfirmationContext> findContext(UUID confirmationId) {
        return jdbc.query("""
                SELECT cc.id, cc.transaction_id, t.card_account_id, ca.application_id,
                       (cc.answered_at IS NOT NULL) AS already_answered
                FROM customer_confirmations cc
                JOIN transactions t ON t.id = cc.transaction_id
                JOIN card_accounts ca ON ca.id = t.card_account_id
                WHERE cc.id = ?
                """,
                rs -> rs.next() ? Optional.of(new ConfirmationContext(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class),
                        rs.getBoolean(5)))
                        : Optional.empty(),
                confirmationId);
    }

    /** @return true when this call recorded the answer, false when it was already answered (idempotent replay). */
    public boolean recordAnswer(UUID confirmationId, String answer) {
        int updated = jdbc.update("""
                UPDATE customer_confirmations SET answer = ?, answered_at = now()
                WHERE id = ? AND answered_at IS NULL
                """, answer, confirmationId);
        return updated == 1;
    }
}
