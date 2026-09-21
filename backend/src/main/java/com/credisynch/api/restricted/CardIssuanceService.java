package com.credisynch.api.restricted;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.persistence.CardAccountRepository;
import com.credisynch.api.persistence.RestrictedRecords.CardAccountRow;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Issues the merchant-locked card on an APPROVE_RESTRICTED decision (ADR 0007): a reduced limit
 * and a velocity cap, locked to the partner the applicant applied through, lifting automatically
 * once the account has had time to accrue trust.
 */
@Service
public class CardIssuanceService {

    private final CardAccountRepository cardAccounts;
    private final AppProperties.Restricted config;

    public CardIssuanceService(CardAccountRepository cardAccounts, AppProperties properties) {
        this.cardAccounts = cardAccounts;
        this.config = properties.restricted();
    }

    /** @param partnerId nullable: an application with no partner gets a velocity cap but no merchant lock. */
    public UUID issue(UUID applicationId, String partnerId) {
        UUID cardAccountId = UUID.randomUUID();
        Instant liftAt = Instant.now().plus(config.liftAfterDays(), ChronoUnit.DAYS);
        cardAccounts.insert(new CardAccountRow(
                cardAccountId, applicationId, "ACTIVE", config.startingLimitMinor(),
                partnerId, config.velocityCapPerDay(), liftAt, Instant.now()));
        return cardAccountId;
    }
}
