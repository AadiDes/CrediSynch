package com.credisynch.api.persistence;

import com.credisynch.api.persistence.RestrictedRecords.CardAccountRow;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CardAccountRepository {

    private final JdbcTemplate jdbc;

    public CardAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(CardAccountRow row) {
        jdbc.update("""
                INSERT INTO card_accounts
                    (id, application_id, status, credit_limit_minor, locked_partner_id,
                     velocity_cap_per_day, restrictions_lift_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                row.id(), row.applicationId(), row.status(), row.creditLimitMinor(), row.lockedPartnerId(),
                row.velocityCapPerDay(),
                row.restrictionsLiftAt() == null ? null : Timestamp.from(row.restrictionsLiftAt()));
    }

    public Optional<CardAccountRow> find(UUID cardAccountId) {
        return jdbc.query("""
                SELECT id, application_id, status, credit_limit_minor, locked_partner_id,
                       velocity_cap_per_day, restrictions_lift_at, opened_at
                FROM card_accounts WHERE id = ?
                """,
                rs -> rs.next() ? Optional.of(new CardAccountRow(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getString(3),
                        rs.getLong(4),
                        rs.getString(5),
                        rs.getObject(6, Integer.class),
                        rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant(),
                        rs.getTimestamp(8).toInstant()))
                        : Optional.empty(),
                cardAccountId);
    }

    public void freeze(UUID cardAccountId) {
        jdbc.update("UPDATE card_accounts SET status = 'FROZEN' WHERE id = ?", cardAccountId);
    }
}
