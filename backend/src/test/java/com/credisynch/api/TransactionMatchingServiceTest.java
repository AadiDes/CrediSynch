package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.credisynch.api.cases.EmbeddingClient;
import com.credisynch.api.config.AppProperties;
import com.credisynch.api.persistence.MerchantRepository;
import com.credisynch.api.persistence.RestrictedRecords.MerchantMatch;
import com.credisynch.api.restricted.TransactionMatchingService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionMatchingServiceTest {

    private final MerchantRepository merchants = mock(MerchantRepository.class);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final AppProperties.Restricted config = new AppProperties.Restricted(50000, 3, 90, 0.45, 0.80);

    @BeforeEach
    void noEmbeddingByDefault() {
        // Every test overrides this with its own stub, declared after service() is constructed,
        // if it needs a real vector - Mockito's "last stubbing wins" means the override must come
        // after this default, not before, so this has to live in a step that runs before every
        // test body rather than inside the service() factory itself.
        given(embeddingClient.embed(anyString())).willReturn(Optional.empty());
    }

    private TransactionMatchingService service() {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ml("http://localhost:8000", 400),
                new AppProperties.Hashing("test-key"),
                new AppProperties.Policy("policy-test", 1500.0, 3.0, 0.8, 5.0, 0.1, 150.0),
                new AppProperties.Graph(720, 1, 3),
                config,
                new AppProperties.Bedrock("apac.amazon.nova-lite-v1:0", "amazon.titan-embed-text-v2:0"),
                new AppProperties.Llm("bedrock", null, "gemini-2.5-flash", "gemini-embedding-001"));
        return new TransactionMatchingService(merchants, embeddingClient, properties);
    }

    private static final float[] SOME_VECTOR = new float[]{0.1f, 0.2f};

    @Test
    @DisplayName("an exact catalog match wins over vector and trigram, with a perfect score")
    void exactMatchWins() {
        given(merchants.findExactMatch("CIRCUIT BYTE ELECTRONICS")).willReturn(Optional.of("partner-electronics"));

        Optional<TransactionMatchingService.Match> match = service().match("Circuit Byte Electronics #4421");

        assertThat(match).isPresent();
        assertThat(match.get().partnerId()).isEqualTo("partner-electronics");
        assertThat(match.get().score()).isEqualTo(1.0);
        assertThat(match.get().method()).isEqualTo("EXACT");
    }

    @Test
    @DisplayName("descriptor punctuation and store numbers are stripped before matching")
    void descriptorIsNormalisedBeforeLookup() {
        given(merchants.findExactMatch("SQ COFFEE SHOP")).willReturn(Optional.of("partner-fuel"));

        service().match("SQ *Coffee-Shop #4421!!");

        org.mockito.Mockito.verify(merchants).findExactMatch("SQ COFFEE SHOP");
    }

    @Test
    @DisplayName("falls back to the best trigram match when nothing is exact and no embedding is available")
    void fallsBackToTrigram() {
        given(merchants.findExactMatch("MYSTERY VENDOR")).willReturn(Optional.empty());
        given(merchants.findBestTrigramMatch("MYSTERY VENDOR"))
                .willReturn(Optional.of(new MerchantMatch("partner-fashion", 0.6)));

        Optional<TransactionMatchingService.Match> match = service().match("Mystery Vendor");

        assertThat(match).isPresent();
        assertThat(match.get().method()).isEqualTo("TRIGRAM");
        assertThat(match.get().score()).isEqualTo(0.6);
    }

    @Test
    @DisplayName("a confident vector match wins over trigram, without trigram even being queried")
    void confidentVectorMatchWinsOverTrigram() {
        given(merchants.findExactMatch(anyString())).willReturn(Optional.empty());
        given(embeddingClient.embed(anyString())).willReturn(Optional.of(SOME_VECTOR));
        given(merchants.findBestVectorMatch(anyString()))
                .willReturn(Optional.of(new MerchantMatch("partner-electronics", 0.92)));

        Optional<TransactionMatchingService.Match> match = service().match("Circuit n Byte Elec");

        assertThat(match).isPresent();
        assertThat(match.get().method()).isEqualTo("VECTOR");
        assertThat(match.get().partnerId()).isEqualTo("partner-electronics");
        org.mockito.Mockito.verify(merchants, org.mockito.Mockito.never()).findBestTrigramMatch(anyString());
    }

    @Test
    @DisplayName("an unconfident vector match falls back to trigram instead of being trusted")
    void unconfidentVectorMatchFallsBackToTrigram() {
        given(merchants.findExactMatch(anyString())).willReturn(Optional.empty());
        given(embeddingClient.embed(anyString())).willReturn(Optional.of(SOME_VECTOR));
        given(merchants.findBestVectorMatch(anyString()))
                .willReturn(Optional.of(new MerchantMatch("partner-electronics", 0.5)));
        given(merchants.findBestTrigramMatch(anyString()))
                .willReturn(Optional.of(new MerchantMatch("partner-fashion", 0.7)));

        Optional<TransactionMatchingService.Match> match = service().match("Something ambiguous");

        assertThat(match).isPresent();
        assertThat(match.get().method()).isEqualTo("TRIGRAM");
        assertThat(match.get().partnerId()).isEqualTo("partner-fashion");
    }

    @Test
    @DisplayName("a messy descriptor where trigram would be wrong is resolved correctly by vector")
    void vectorSucceedsWhereTrigramWouldFail() {
        // "CBE" shares almost no trigrams with "CIRCUIT BYTE ELECTRONICS" - a character-overlap
        // matcher has nothing to work with here, but an embedding of the abbreviation can still be
        // semantically close to the merchant's descriptor.
        String messyDescriptor = "CBE #99120";
        given(merchants.findExactMatch(anyString())).willReturn(Optional.empty());
        given(embeddingClient.embed(anyString())).willReturn(Optional.of(SOME_VECTOR));
        given(merchants.findBestVectorMatch(anyString()))
                .willReturn(Optional.of(new MerchantMatch("partner-electronics", 0.88)));
        given(merchants.findBestTrigramMatch(anyString()))
                .willReturn(Optional.of(new MerchantMatch("partner-electronics", 0.05)));

        Optional<TransactionMatchingService.Match> match = service().match(messyDescriptor);

        assertThat(match).isPresent();
        assertThat(match.get().method()).isEqualTo("VECTOR");
        assertThat(match.get().score()).isGreaterThan(0.05);
    }

    @Test
    @DisplayName("an embedding call failure degrades silently to trigram, never throws")
    void embeddingFailureDegradesToTrigram() {
        given(merchants.findExactMatch(anyString())).willReturn(Optional.empty());
        given(embeddingClient.embed(anyString())).willThrow(new RuntimeException("network blip"));
        given(merchants.findBestTrigramMatch(anyString()))
                .willReturn(Optional.of(new MerchantMatch("partner-fuel", 0.65)));

        Optional<TransactionMatchingService.Match> match = service().match("Quickfuel Stn 12");

        assertThat(match).isPresent();
        assertThat(match.get().method()).isEqualTo("TRIGRAM");
    }

    @Test
    @DisplayName("trigram confidence threshold: at or above trusted, below asks the customer")
    void trigramConfidenceThreshold() {
        TransactionMatchingService service = service();
        assertThat(service.isConfident(0.45)).isTrue();
        assertThat(service.isConfident(0.44)).isFalse();
    }
}
