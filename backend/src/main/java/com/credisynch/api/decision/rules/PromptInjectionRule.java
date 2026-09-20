package com.credisynch.api.decision.rules;

import com.credisynch.api.decision.ApplicationRequest;
import com.credisynch.api.decision.DecisionAction;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Applicant free text reaches the AI layer as untrusted data. Someone trying to steer that
 * model is not a confused customer, so the attempt itself is treated as a fraud signal
 * and the application is routed to a human.
 */
@Component
public class PromptInjectionRule implements Rule {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("ignore (all |any )?(previous|prior|above) instructions"),
            Pattern.compile("disregard (the )?(previous|system|above)"),
            Pattern.compile("you are now|act as (an?|the) (admin|system|analyst)"),
            Pattern.compile("system prompt|developer message"),
            Pattern.compile("approve (this|the) (application|loan|account)( immediately| now)?"),
            Pattern.compile("set (fraud_probability|risk|score) (to )?(0|zero|low)"));

    @Override
    public String id() {
        return "PROMPT_INJECTION_ATTEMPT";
    }

    @Override
    public RuleOutcome evaluate(ApplicationRequest request) {
        String text = request.freeText();
        if (text == null || text.isBlank()) {
            return RuleOutcome.pass(id());
        }
        String normalised = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(normalised).find()) {
                return RuleOutcome.fire(id(), DecisionAction.REVIEW,
                        "Free-text field contains an instruction aimed at the AI layer");
            }
        }
        return RuleOutcome.pass(id());
    }
}
