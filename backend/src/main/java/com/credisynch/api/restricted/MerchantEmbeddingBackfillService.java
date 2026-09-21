package com.credisynch.api.restricted;

import com.credisynch.api.cases.EmbeddingClient;
import com.credisynch.api.persistence.MerchantRepository;
import com.credisynch.api.persistence.VectorFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * "Embed the merchant catalog once" (Module A / ADR 0007's VECTOR match path): on startup, embeds
 * whichever merchants don't have a descriptor_embedding yet via the active {@link EmbeddingClient}
 * (Gemini or Bedrock, whichever is configured). Idempotent - {@link MerchantRepository}'s missing-
 * embedding query means an already-embedded catalog is a no-op on every subsequent restart.
 */
@Component
public class MerchantEmbeddingBackfillService {

    private static final Logger log = LoggerFactory.getLogger(MerchantEmbeddingBackfillService.class);

    private final MerchantRepository merchants;
    private final EmbeddingClient embeddingClient;

    public MerchantEmbeddingBackfillService(MerchantRepository merchants, EmbeddingClient embeddingClient) {
        this.merchants = merchants;
        this.embeddingClient = embeddingClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        List<String> pending = merchants.findPartnerIdsMissingEmbedding();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Embedding {} merchant(s) missing a descriptor_embedding", pending.size());
        for (String partnerId : pending) {
            String descriptor = merchants.findDescriptorPattern(partnerId);
            embeddingClient.embed(descriptor).ifPresentOrElse(
                    vector -> merchants.updateEmbedding(partnerId, VectorFormat.literal(vector)),
                    () -> log.warn("Could not embed merchant {}; VECTOR matching will skip it until a later restart",
                            partnerId));
        }
    }
}
