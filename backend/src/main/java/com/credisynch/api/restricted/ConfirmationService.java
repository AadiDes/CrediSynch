package com.credisynch.api.restricted;

import com.credisynch.api.persistence.CardAccountRepository;
import com.credisynch.api.persistence.CaseRepository;
import com.credisynch.api.persistence.ConfirmationRepository;
import com.credisynch.api.persistence.RestrictedRecords.ConfirmationContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Was this you?" (ADR 0007): the customer's answer becomes a case label within minutes instead of
 * an analyst finding it weeks later, and NOT_ME freezes the card immediately.
 */
@Service
public class ConfirmationService {

    private final ConfirmationRepository confirmations;
    private final CardAccountRepository cardAccounts;
    private final CaseRepository cases;

    public ConfirmationService(ConfirmationRepository confirmations, CardAccountRepository cardAccounts,
                               CaseRepository cases) {
        this.confirmations = confirmations;
        this.cardAccounts = cardAccounts;
        this.cases = cases;
    }

    @Transactional
    public void answer(UUID confirmationId, String answer, String actor) {
        ConfirmationContext context = confirmations.findContext(confirmationId)
                .orElseThrow(() -> new IllegalArgumentException("No such confirmation: " + confirmationId));

        if (context.alreadyAnswered()) {
            return; // idempotent: the customer's first answer stands, a retry is not an error
        }
        if (!confirmations.recordAnswer(confirmationId, answer)) {
            return; // lost the race to another concurrent answer; the other one stands
        }

        String label = "NOT_ME".equals(answer) ? "FRAUD" : "LEGITIMATE";
        if ("NOT_ME".equals(answer)) {
            cardAccounts.freeze(context.cardAccountId());
        }
        cases.findLatestCaseForApplication(context.applicationId())
                .ifPresent(caseId -> cases.insertLabel(caseId, label, "CUSTOMER_CONFIRM", actor,
                        "Customer answered '" + answer + "' on transaction " + context.transactionId()));
    }
}
