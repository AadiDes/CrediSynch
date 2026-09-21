package com.credisynch.api.decision.rules;

import com.credisynch.api.decision.ApplicationRequest;
import com.credisynch.api.decision.DecisionAction;
import org.springframework.stereotype.Component;

/** Cheap structural checks that no model should have to learn. */
@Component
public class ImpossibleValueRule implements Rule {

    @Override
    public String id() {
        return "IMPOSSIBLE_VALUES";
    }

    @Override
    public RuleOutcome evaluate(ApplicationRequest request) {
        Object age = request.featuresOrEmpty().get("customer_age");
        if (age instanceof Number number && (number.doubleValue() < 18 || number.doubleValue() > 110)) {
            return RuleOutcome.fire(id(), DecisionAction.DECLINE,
                    "Applicant age outside the eligible range");
        }
        Object income = request.featuresOrEmpty().get("income");
        if (income instanceof Number number && number.doubleValue() < 0) {
            return RuleOutcome.fire(id(), DecisionAction.REVIEW, "Declared income is negative");
        }
        return RuleOutcome.pass(id());
    }
}
