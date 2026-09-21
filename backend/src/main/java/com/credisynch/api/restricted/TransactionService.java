package com.credisynch.api.restricted;

import com.credisynch.api.persistence.CardAccountRepository;
import com.credisynch.api.persistence.ConfirmationRepository;
import com.credisynch.api.persistence.IdempotencyRepository;
import com.credisynch.api.persistence.RestrictedRecords.CardAccountRow;
import com.credisynch.api.persistence.RestrictedRecords.TransactionRow;
import com.credisynch.api.persistence.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorises a purchase on a restricted card: applies the merchant lock, the velocity cap, and
 * asks the customer instead of guessing when the descriptor match is not confident (ADR 0007).
 */
@Service
public class TransactionService {

    private final CardAccountRepository cardAccounts;
    private final TransactionRepository transactions;
    private final ConfirmationRepository confirmations;
    private final TransactionMatchingService matching;
    private final IdempotencyRepository idempotency;
    private final ObjectMapper objectMapper;

    public TransactionService(CardAccountRepository cardAccounts, TransactionRepository transactions,
                              ConfirmationRepository confirmations, TransactionMatchingService matching,
                              IdempotencyRepository idempotency, ObjectMapper objectMapper) {
        this.cardAccounts = cardAccounts;
        this.transactions = transactions;
        this.confirmations = confirmations;
        this.matching = matching;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionResponse authorise(TransactionRequest request, String idempotencyKey) {
        String digest = digestOf(request);
        Optional<IdempotencyRepository.Stored> existing = idempotency.find(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), digest, idempotencyKey);
        }
        if (!idempotency.tryClaim(idempotencyKey, digest)) {
            return replay(idempotency.find(idempotencyKey).orElseThrow(), digest, idempotencyKey);
        }

        CardAccountRow card = cardAccounts.find(request.cardAccountId())
                .orElseThrow(() -> new IllegalArgumentException("No such card account: " + request.cardAccountId()));

        TransactionResponse response = decide(request, card);

        idempotency.storeResponse(idempotencyKey, null, toJson(response));
        return response;
    }

    private TransactionResponse decide(TransactionRequest request, CardAccountRow card) {
        UUID transactionId = UUID.randomUUID();

        if (!"ACTIVE".equals(card.status())) {
            return finish(transactionId, request, null, null, null, "DECLINED_RISK",
                    "Card is " + card.status().toLowerCase(java.util.Locale.ROOT));
        }

        Optional<TransactionMatchingService.Match> match = matching.match(request.rawDescriptor());
        boolean confident = match.isPresent() && matching.isConfident(match.get().score());

        if (!confident) {
            String matchedPartnerId = match.map(TransactionMatchingService.Match::partnerId).orElse(null);
            Double score = match.map(TransactionMatchingService.Match::score).orElse(null);
            transactions.insert(new TransactionRow(transactionId, card.id(), request.amountMinor(),
                    request.rawDescriptor(), matchedPartnerId, score, null, "CONFIRM_PENDING",
                    "Merchant match was not confident enough to decide automatically"));
            UUID confirmationId = UUID.randomUUID();
            confirmations.insert(confirmationId, transactionId);
            return new TransactionResponse(transactionId, "CONFIRM_PENDING", matchedPartnerId, score, null,
                    confirmationId);
        }

        TransactionMatchingService.Match confidentMatch = match.get();
        if (card.lockActive() && !card.lockedPartnerId().equals(confidentMatch.partnerId())) {
            return finish(transactionId, request, confidentMatch.partnerId(), confidentMatch.score(),
                    confidentMatch.method(), "DECLINED_LOCK",
                    "Card is locked to " + card.lockedPartnerId());
        }

        if (card.velocityCapPerDay() != null) {
            int approvedToday = transactions.countApprovedSince(card.id(), Instant.now().minus(Duration.ofDays(1)));
            if (approvedToday >= card.velocityCapPerDay()) {
                return finish(transactionId, request, confidentMatch.partnerId(), confidentMatch.score(),
                        confidentMatch.method(), "DECLINED_VELOCITY",
                        "Velocity cap of " + card.velocityCapPerDay() + " approved transactions/day reached");
            }
        }

        return finish(transactionId, request, confidentMatch.partnerId(), confidentMatch.score(),
                confidentMatch.method(), "APPROVED", null);
    }

    private TransactionResponse finish(UUID transactionId, TransactionRequest request, String matchedPartnerId,
                                       Double matchScore, String matchMethod, String outcome, String reason) {
        transactions.insert(new TransactionRow(transactionId, request.cardAccountId(), request.amountMinor(),
                request.rawDescriptor(), matchedPartnerId, matchScore, matchMethod, outcome, reason));
        return new TransactionResponse(transactionId, outcome, matchedPartnerId, matchScore, matchMethod, null);
    }

    private TransactionResponse replay(IdempotencyRepository.Stored stored, String digest, String key) {
        if (!stored.requestDigest().equals(digest)) {
            throw new IllegalArgumentException(
                    "Idempotency key " + key + " was already used with a different payload");
        }
        try {
            return objectMapper.readValue(stored.responseBody(), TransactionResponse.class);
        } catch (JsonProcessingException | NullPointerException e) {
            throw new IllegalStateException("Stored idempotent response could not be read", e);
        }
    }

    private String digestOf(TransactionRequest request) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to digest request", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
