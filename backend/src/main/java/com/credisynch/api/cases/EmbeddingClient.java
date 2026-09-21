package com.credisynch.api.cases;

import java.util.Optional;

/**
 * ADR 0003: embeddings fixed at 1024 dimensions (Titan Text Embeddings V2's default). Empty means
 * no embedding could be produced - no model access, throttling, a network blip - not an error;
 * callers treat a case with no embedding as simply not yet indexed for similarity search.
 */
public interface EmbeddingClient {
    Optional<float[]> embed(String text);
}
