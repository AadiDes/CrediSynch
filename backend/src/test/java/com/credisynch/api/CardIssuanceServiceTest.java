package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.persistence.CardAccountRepository;
import com.credisynch.api.persistence.RestrictedRecords.CardAccountRow;
import com.credisynch.api.restricted.CardIssuanceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CardIssuanceServiceTest {

    private final CardAccountRepository cardAccounts = mock(CardAccountRepository.class);
    private final AppProperties.Restricted config = new AppProperties.Restricted(50000, 3, 90, 0.45, 0.75);

    private CardIssuanceService service() {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ml("http://localhost:8000", 400),
                new AppProperties.Hashing("test-key"),
                new AppProperties.Policy("policy-test", 1500.0, 3.0, 0.8, 5.0, 0.1, 150.0),
                new AppProperties.Graph(720, 1, 3),
                config,
                new AppProperties.Bedrock("apac.amazon.nova-lite-v1:0", "amazon.titan-embed-text-v2:0"),
                new AppProperties.Llm("bedrock", null, "gemini-2.5-flash", "gemini-embedding-001"));
        return new CardIssuanceService(cardAccounts, properties);
    }

    @Test
    @DisplayName("issues an active card locked to the partner, with the configured limit and cap")
    void issuesCardWithConfiguredDefaults() {
        UUID applicationId = UUID.randomUUID();

        UUID cardAccountId = service().issue(applicationId, "partner-electronics");

        ArgumentCaptor<CardAccountRow> captor = ArgumentCaptor.forClass(CardAccountRow.class);
        verify(cardAccounts).insert(captor.capture());
        CardAccountRow row = captor.getValue();
        assertThat(row.id()).isEqualTo(cardAccountId);
        assertThat(row.applicationId()).isEqualTo(applicationId);
        assertThat(row.status()).isEqualTo("ACTIVE");
        assertThat(row.creditLimitMinor()).isEqualTo(50000);
        assertThat(row.lockedPartnerId()).isEqualTo("partner-electronics");
        assertThat(row.velocityCapPerDay()).isEqualTo(3);
        assertThat(row.restrictionsLiftAt()).isAfter(Instant.now().plusSeconds(89L * 24 * 3600));
        assertThat(row.restrictionsLiftAt()).isBefore(Instant.now().plusSeconds(91L * 24 * 3600));
    }

    @Test
    @DisplayName("an application with no partner gets a velocity cap but no merchant lock")
    void nullPartnerMeansNoLock() {
        service().issue(UUID.randomUUID(), null);

        ArgumentCaptor<CardAccountRow> captor = ArgumentCaptor.forClass(CardAccountRow.class);
        verify(cardAccounts).insert(captor.capture());
        assertThat(captor.getValue().lockedPartnerId()).isNull();
    }
}
