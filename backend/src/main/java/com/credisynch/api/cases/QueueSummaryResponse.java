package com.credisynch.api.cases;

import java.util.Map;

public record QueueSummaryResponse(int openCount, int inReviewCount, int closedCount, Map<Integer, Integer> byPriority) {}
