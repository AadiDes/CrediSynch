package com.credisynch.api.decision.rules;

import com.credisynch.api.decision.DecisionAction;

/**
 * A rule either passes, or fires with the floor action it forces.
 * Rules can only make a decision stricter, never more permissive.
 */
public record RuleOutcome(String ruleId, boolean fired, DecisionAction floor, String explanation) {

    public static RuleOutcome pass(String ruleId) {
        return new RuleOutcome(ruleId, false, null, null);
    }

    public static RuleOutcome fire(String ruleId, DecisionAction floor, String explanation) {
        return new RuleOutcome(ruleId, true, floor, explanation);
    }
}
