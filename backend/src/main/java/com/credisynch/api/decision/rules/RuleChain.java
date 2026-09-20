package com.credisynch.api.decision.rules;

import com.credisynch.api.decision.ApplicationRequest;
import com.credisynch.api.decision.DecisionAction;
import java.util.List;
import org.springframework.stereotype.Component;

/** Runs every rule (no short-circuit, so the case shows all evidence) and returns the strictest floor. */
@Component
public class RuleChain {

    private final List<Rule> rules;

    public RuleChain(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public record Result(List<RuleOutcome> fired, DecisionAction floor) {
        public List<String> firedIds() {
            return fired.stream().map(RuleOutcome::ruleId).toList();
        }
    }

    public Result evaluate(ApplicationRequest request) {
        List<RuleOutcome> fired = rules.stream()
                .map(rule -> rule.evaluate(request))
                .filter(RuleOutcome::fired)
                .toList();
        DecisionAction floor = fired.stream()
                .map(RuleOutcome::floor)
                .max(java.util.Comparator.comparingInt(Enum::ordinal))
                .orElse(null);
        return new Result(fired, floor);
    }
}
