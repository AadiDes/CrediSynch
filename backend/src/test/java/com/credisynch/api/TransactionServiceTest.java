package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.credisynch.api.persistence.CardAccountRepository;
import com.credisynch.api.persistence.ConfirmationRepository;
import com.credisynch.api.persistence.IdempotencyRepository;
import com.credisynch.api.persistence.RestrictedRecords.CardAccountRow;
import com.credisynch.api.persistence.TransactionRepository;
import com.credisynch.api.restricted.TransactionMatchingService;
import com.credisynch.api.restricted.TransactionRequest;
import com.credisynch.api.restricted.TransactionResponse;
import com.credisynch.api.restricted.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionServiceTest {

    private final CardAccountRepository cardAccounts = mock(CardAccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final ConfirmationRepository confirmations = mock(ConfirmationRepository.class);
    private final TransactionMatchingService matching = mock(TransactionMatchingService.class);
    private final IdempotencyRepository idempotency = mock(IdempotencyRepository.class);
    private final TransactionService service = new TransactionService(
            cardAccounts, transactions, confirmations, matching, idempotency, new ObjectMapper());

    private final UUID cardAccountId = UUID.randomUUID();

    @BeforeEach
    void freshIdempotencyKey() {
        given(idempotency.find(org.mockito.ArgumentMatchers.anyString())).willReturn(Optional.empty());
        given(idempotency.tryClaim(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .willReturn(true);
    }

    private CardAccountRow activeCard(String lockedPartnerId, Instant liftAt, Integer velocityCap) {
        return new CardAccountRow(cardAccountId, UUID.randomUUID(), "ACTIVE", 50000,
                lockedPartnerId, velocityCap, liftAt, Instant.now());
    }

    private TransactionRequest request(String descriptor) {
        return new TransactionRequest(cardAccountId, 1999, descriptor);
    }

    @Test
    @DisplayName("an unknown card account is rejected before any matching happens")
    void unknownCardAccountIsRejected() {
        given(cardAccounts.find(cardAccountId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorise(request("Anything"), "key-12345678"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(matching, never()).match(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("a confident match to the locked partner, under the velocity cap, is approved")
    void approvedWhenMatchedToLockedPartner() {
        given(cardAccounts.find(cardAccountId))
                .willReturn(Optional.of(activeCard("partner-electronics", Instant.now().plusSeconds(3600), 3)));
        given(matching.match("Circuit Byte Electronics"))
                .willReturn(Optional.of(new TransactionMatchingService.Match("partner-electronics", 0.9, "TRIGRAM")));
        given(matching.isConfident(0.9)).willReturn(true);
        given(transactions.countApprovedSince(any(), any())).willReturn(0);

        TransactionResponse response = service.authorise(request("Circuit Byte Electronics"), "key-12345678");

        assertThat(response.outcome()).isEqualTo("APPROVED");
        assertThat(response.matchedPartnerId()).isEqualTo("partner-electronics");
        assertThat(response.confirmationId()).isNull();
    }

    @Test
    @DisplayName("a confident match to a different partner while the lock is active is declined")
    void declinedWhenLockedToAnotherPartner() {
        given(cardAccounts.find(cardAccountId))
                .willReturn(Optional.of(activeCard("partner-electronics", Instant.now().plusSeconds(3600), 3)));
        given(matching.match("GreenCart Grocers"))
                .willReturn(Optional.of(new TransactionMatchingService.Match("partner-groceries", 0.95, "TRIGRAM")));
        given(matching.isConfident(0.95)).willReturn(true);

        TransactionResponse response = service.authorise(request("GreenCart Grocers"), "key-12345678");

        assertThat(response.outcome()).isEqualTo("DECLINED_LOCK");
    }

    @Test
    @DisplayName("a lifted lock (restrictions_lift_at in the past) no longer blocks other merchants")
    void liftedLockAllowsOtherMerchants() {
        given(cardAccounts.find(cardAccountId))
                .willReturn(Optional.of(activeCard("partner-electronics", Instant.now().minusSeconds(1), 3)));
        given(matching.match("GreenCart Grocers"))
                .willReturn(Optional.of(new TransactionMatchingService.Match("partner-groceries", 0.95, "TRIGRAM")));
        given(matching.isConfident(0.95)).willReturn(true);
        given(transactions.countApprovedSince(any(), any())).willReturn(0);

        TransactionResponse response = service.authorise(request("GreenCart Grocers"), "key-12345678");

        assertThat(response.outcome()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("hitting the daily velocity cap declines even a correctly matched purchase")
    void declinedOnVelocityCap() {
        given(cardAccounts.find(cardAccountId))
                .willReturn(Optional.of(activeCard("partner-electronics", Instant.now().plusSeconds(3600), 2)));
        given(matching.match("Circuit Byte Electronics"))
                .willReturn(Optional.of(new TransactionMatchingService.Match("partner-electronics", 0.9, "TRIGRAM")));
        given(matching.isConfident(0.9)).willReturn(true);
        given(transactions.countApprovedSince(any(), any())).willReturn(2);

        TransactionResponse response = service.authorise(request("Circuit Byte Electronics"), "key-12345678");

        assertThat(response.outcome()).isEqualTo("DECLINED_VELOCITY");
    }

    @Test
    @DisplayName("a frozen or closed card is declined for risk before any matching")
    void frozenCardIsDeclinedForRisk() {
        given(cardAccounts.find(cardAccountId))
                .willReturn(Optional.of(new CardAccountRow(cardAccountId, UUID.randomUUID(), "FROZEN", 50000,
                        "partner-electronics", 3, null, Instant.now())));

        TransactionResponse response = service.authorise(request("Anything"), "key-12345678");

        assertThat(response.outcome()).isEqualTo("DECLINED_RISK");
        verify(matching, never()).match(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("an unconfident match asks the customer instead of guessing")
    void unconfidentMatchAsksTheCustomer() {
        given(cardAccounts.find(cardAccountId))
                .willReturn(Optional.of(activeCard("partner-electronics", Instant.now().plusSeconds(3600), 3)));
        given(matching.match("Unknown Merchant"))
                .willReturn(Optional.of(new TransactionMatchingService.Match("partner-fashion", 0.2, "TRIGRAM")));
        given(matching.isConfident(0.2)).willReturn(false);

        TransactionResponse response = service.authorise(request("Unknown Merchant"), "key-12345678");

        assertThat(response.outcome()).isEqualTo("CONFIRM_PENDING");
        assertThat(response.confirmationId()).isNotNull();
        verify(confirmations).insert(any(), any());
    }

    @Test
    @DisplayName("a replayed idempotency key returns the original outcome without re-deciding")
    void idempotentReplayReturnsOriginal() throws Exception {
        String key = "replay-key-1";
        TransactionResponse original = new TransactionResponse(UUID.randomUUID(), "APPROVED", "partner-electronics",
                0.9, "TRIGRAM", null);
        String digest = digestOf(request("Circuit Byte Electronics"));
        given(idempotency.find(key)).willReturn(Optional.of(new IdempotencyRepository.Stored(
                digest, new ObjectMapper().writeValueAsString(original))));

        TransactionResponse response = service.authorise(request("Circuit Byte Electronics"), key);

        assertThat(response.transactionId()).isEqualTo(original.transactionId());
        verify(cardAccounts, never()).find(any());
    }

    private String digestOf(TransactionRequest request) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(new ObjectMapper().writeValueAsBytes(request));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
