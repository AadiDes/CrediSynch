package com.credisynch.api.cases;

import java.util.List;

public record CasePageResponse(List<CaseSummaryResponse> items, String nextCursor) {}
