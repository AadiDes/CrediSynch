package com.credisynch.api.restricted;

import com.credisynch.api.cases.EmbeddingClient;
import com.credisynch.api.config.AppProperties;
import com.credisynch.api.persistence.MerchantRepository;
import com.credisynch.api.persistence.RestrictedRecords.MerchantMatch;
import com.credisynch.api.persistence.VectorFormat;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves a messy card descriptor ("SQ *COFFEE SHOP 4421") to a merchant partner - the Module A
 * entity-resolution claim in the pitch (ADR 0007, Capital One-benchmarked).
 *
 * Order: EXACT (free, instant) -&gt; VECTOR (embedding cosine similarity - catches paraphrases and
 * abbreviations trigram literally cannot, e.g. "CBE ELECTRONICS" for "Circuit &amp; Byte
 * Electronics") -&gt; TRIGRAM (character-overlap fallback, catches typos/truncation VECTOR can miss)
 * -&gt; none. VECTOR silently degrades to TRIGRAM-only when the embedding client returns nothing (no
 * catalog embeddings yet, or the call fails) - see {@link MerchantEmbeddingBackfillService}.
 */
@Component
public class TransactionMatchingService {

    private static final Logger log = LoggerFactory.getLogger(TransactionMatchingService.class);

    public record Match(String partnerId, double score, String method) {}

    private final MerchantRepository merchants;
    private final EmbeddingClient embeddingClient;
    private final AppProperties.Restricted config;

    public TransactionMatchingService(MerchantRepository merchants, EmbeddingClient embeddingClient,
                                      AppProperties properties) {
        this.merchants = merchants;
        this.embeddingClient = embeddingClient;
        this.config = properties.restricted();
    }

    public Optional<Match> match(String rawDescriptor) {
        String normalised = normalise(rawDescriptor);

        Optional<String> exact = merchants.findExactMatch(normalised);
        if (exact.isPresent()) {
            return Optional.of(new Match(exact.get(), 1.0, "EXACT"));
        }

        Optional<Match> vector = matchByVector(normalised);
        if (vector.isPresent() && isVectorConfident(vector.get().score())) {
            return vector;
        }

        return merchants.findBestTrigramMatch(normalised).map(m -> new Match(m.partnerId(), m.score(), "TRIGRAM"));
    }

    private Optional<Match> matchByVector(String normalisedDescriptor) {
        try {
            return embeddingClient.embed(normalisedDescriptor)
                    .flatMap(vector -> merchants.findBestVectorMatch(VectorFormat.literal(vector)))
                    .map(m -> new Match(m.partnerId(), m.score(), "VECTOR"));
        } catch (Exception e) {
            log.warn("Vector match failed; falling back to trigram", e);
            return Optional.empty();
        }
    }

    public boolean isConfident(double score) {
        return score >= config.matchConfidentThreshold();
    }

    private boolean isVectorConfident(double score) {
        return score >= config.vectorMatchConfidentThreshold();
    }

    /** Descriptors carry store numbers, POS prefixes and punctuation that trigram matching ignores best without. */
    private String normalise(String rawDescriptor) {
        return rawDescriptor.toUpperCase(Locale.ROOT).replaceAll("[^A-Z ]", " ").replaceAll("\\s+", " ").trim();
    }
}
