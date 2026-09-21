package com.credisynch.api.persistence;

import java.time.Instant;
import java.util.UUID;

/** Row shapes for module A: restricted approvals (ADR 0007). Mirrors the schema, SQL-first (ADR 0008). */
public final class RestrictedRecords {

    private RestrictedRecords() {}

    public record CardAccountRow(
            UUID id,
            UUID applicationId,
            String status,
            long creditLimitMinor,
            String lockedPartnerId,
            Integer velocityCapPerDay,
            Instant restrictionsLiftAt,
            Instant openedAt) {

        public boolean lockActive() {
            return lockedPartnerId != null && (restrictionsLiftAt == null || Instant.now().isBefore(restrictionsLiftAt));
        }
    }

    public record MerchantMatch(String partnerId, double score) {}

    public record TransactionRow(
            UUID id,
            UUID cardAccountId,
            long amountMinor,
            String rawDescriptor,
            String matchedPartnerId,
            Double matchScore,
            String matchMethod,
            String outcome,
            String reason) {}

    public record ConfirmationContext(
            UUID confirmationId, UUID transactionId, UUID cardAccountId, UUID applicationId, boolean alreadyAnswered) {}
}
