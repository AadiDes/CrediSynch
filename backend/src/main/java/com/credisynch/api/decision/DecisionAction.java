package com.credisynch.api.decision;

/** The actions the policy engine may emit, cheapest containment first. */
public enum DecisionAction {
    APPROVE,
    STEP_UP,
    APPROVE_RESTRICTED,
    REVIEW,
    DECLINE;

    public boolean opensCase() {
        return this == REVIEW || this == DECLINE || this == APPROVE_RESTRICTED;
    }
}
