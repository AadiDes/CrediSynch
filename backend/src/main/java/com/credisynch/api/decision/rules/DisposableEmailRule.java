package com.credisynch.api.decision.rules;

import com.credisynch.api.decision.ApplicationRequest;
import com.credisynch.api.decision.DecisionAction;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Throwaway mailbox domains are a weak signal on their own, so this only forces a step-up.
 * A blocklist of real fraud indicators would live in the database in production.
 */
@Component
public class DisposableEmailRule implements Rule {

    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
            "mailinator.com", "guerrillamail.com", "10minutemail.com", "tempmail.com", "yopmail.com");

    @Override
    public String id() {
        return "DISPOSABLE_EMAIL";
    }

    @Override
    public RuleOutcome evaluate(ApplicationRequest request) {
        String email = request.applicant().email().toLowerCase(Locale.ROOT);
        int at = email.indexOf('@');
        String domain = at >= 0 ? email.substring(at + 1) : "";
        if (DISPOSABLE_DOMAINS.contains(domain)) {
            return RuleOutcome.fire(id(), DecisionAction.STEP_UP,
                    "Application uses a disposable email domain");
        }
        return RuleOutcome.pass(id());
    }
}
