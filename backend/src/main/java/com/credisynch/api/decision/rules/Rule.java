package com.credisynch.api.decision.rules;

import com.credisynch.api.decision.ApplicationRequest;

public interface Rule {
    String id();

    RuleOutcome evaluate(ApplicationRequest request);
}
