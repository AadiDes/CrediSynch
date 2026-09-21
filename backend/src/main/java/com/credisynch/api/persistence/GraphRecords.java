package com.credisynch.api.persistence;

import java.util.UUID;

/** Row shapes for ring detection (off the decision hot path). Mirrors the schema (ADR 0008). */
public final class GraphRecords {

    private GraphRecords() {}

    public record EntityLinkRow(UUID applicationId, String entityType, String entityHash) {}
}
