package com.credisynch.api.restricted;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.persistence.MerchantRepository;
import com.credisynch.api.persistence.RestrictedRecords.MerchantMatch;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Resolves a messy card descriptor ("SQ *COFFEE SHOP 4421") to a merchant partner.
 *
 * ADR 0003 designs vector similarity (Titan embeddings via Bedrock) as the eventual matcher; that
 * needs Bedrock model access this deployment does not have provisioned, so this stays on the
 * documented fallback path - exact match, then Postgres trigram similarity (pg_trgm) - which needs
 * no external service and is already exact enough for a fixed merchant catalog. Swapping in a
 * VECTOR method later only means adding another branch here; matchMethod already reserves it.
 */
@Component
public class TransactionMatchingService {

    public record Match(String partnerId, double score, String method) {}

    private final MerchantRepository merchants;
    private final AppProperties.Restricted config;

    public TransactionMatchingService(MerchantRepository merchants, AppProperties properties) {
        this.merchants = merchants;
        this.config = properties.restricted();
    }

    public Optional<Match> match(String rawDescriptor) {
        String normalised = normalise(rawDescriptor);
        Optional<String> exact = merchants.findExactMatch(normalised);
        if (exact.isPresent()) {
            return Optional.of(new Match(exact.get(), 1.0, "EXACT"));
        }
        return merchants.findBestTrigramMatch(normalised).map(m -> new Match(m.partnerId(), m.score(), "TRIGRAM"));
    }

    public boolean isConfident(double score) {
        return score >= config.matchConfidentThreshold();
    }

    /** Descriptors carry store numbers, POS prefixes and punctuation that trigram matching ignores best without. */
    private String normalise(String rawDescriptor) {
        return rawDescriptor.toUpperCase(Locale.ROOT).replaceAll("[^A-Z ]", " ").replaceAll("\\s+", " ").trim();
    }
}
