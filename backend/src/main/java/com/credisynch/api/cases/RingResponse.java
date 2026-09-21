package com.credisynch.api.cases;

import java.util.List;
import java.util.UUID;

public record RingResponse(UUID ringId, String algorithm, int size, Double density, List<SharedEntity> sharedEntities) {

    public record SharedEntity(String entityType, int applicationCount) {}
}
