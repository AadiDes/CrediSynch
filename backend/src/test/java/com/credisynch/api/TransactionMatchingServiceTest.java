package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.persistence.MerchantRepository;
import com.credisynch.api.persistence.RestrictedRecords.MerchantMatch;
import com.credisynch.api.restricted.TransactionMatchingService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionMatchingServiceTest {

    private final MerchantRepository merchants = mock(MerchantRepository.class);
    private final AppProperties.Restricted config = new AppProperties.Restricted(50000, 3, 90, 0.45);

    private TransactionMatchingService service() {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ml("http://localhost:8000", 400),
                new AppProperties.Hashing("test-key"),
                new AppProperties.Policy("policy-test", 1500.0, 3.0, 0.8, 5.0, 0.1, 150.0),
                new AppProperties.Graph(720, 1, 3),
                config,
                new AppProperties.Bedrock("apac.amazon.nova-lite-v1:0", "amazon.titan-embed-text-v2:0"));
        return new TransactionMatchingService(merchants, properties);
    }

    @Test
    @DisplayName("an exact catalog match wins over trigram, with a perfect score")
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
    @DisplayName("falls back to the best trigram match when nothing is exact")
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
    @DisplayName("a score at or above the confidence threshold is trusted, below it is not")
    void confidenceThreshold() {
        TransactionMatchingService service = service();
        assertThat(service.isConfident(0.45)).isTrue();
        assertThat(service.isConfident(0.44)).isFalse();
    }
}
