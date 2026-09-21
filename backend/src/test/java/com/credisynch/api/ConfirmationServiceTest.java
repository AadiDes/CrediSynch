package com.credisynch.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.credisynch.api.persistence.CardAccountRepository;
import com.credisynch.api.persistence.CaseRepository;
import com.credisynch.api.persistence.ConfirmationRepository;
import com.credisynch.api.persistence.RestrictedRecords.ConfirmationContext;
import com.credisynch.api.restricted.ConfirmationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfirmationServiceTest {

    private final ConfirmationRepository confirmations = mock(ConfirmationRepository.class);
    private final CardAccountRepository cardAccounts = mock(CardAccountRepository.class);
    private final CaseRepository cases = mock(CaseRepository.class);
    private final ConfirmationService service = new ConfirmationService(confirmations, cardAccounts, cases);

    private final UUID confirmationId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();
    private final UUID cardAccountId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();

    private ConfirmationContext freshContext() {
        return new ConfirmationContext(confirmationId, transactionId, cardAccountId, applicationId, false);
    }

    @Test
    @DisplayName("NOT_ME freezes the card and labels the case FRAUD")
    void notMeFreezesCardAndLabelsFraud() {
        given(confirmations.findContext(confirmationId)).willReturn(Optional.of(freshContext()));
        given(confirmations.recordAnswer(confirmationId, "NOT_ME")).willReturn(true);
        given(cases.findLatestCaseForApplication(applicationId)).willReturn(Optional.of(caseId));

        service.answer(confirmationId, "NOT_ME", "applicant-1");

        verify(cardAccounts).freeze(cardAccountId);
        verify(cases).insertLabel(eq(caseId), eq("FRAUD"), eq("CUSTOMER_CONFIRM"), eq("applicant-1"), anyString());
    }

    @Test
    @DisplayName("YES_IT_WAS_ME labels the case LEGITIMATE without freezing the card")
    void yesItWasMeLabelsLegitimate() {
        given(confirmations.findContext(confirmationId)).willReturn(Optional.of(freshContext()));
        given(confirmations.recordAnswer(confirmationId, "YES_IT_WAS_ME")).willReturn(true);
        given(cases.findLatestCaseForApplication(applicationId)).willReturn(Optional.of(caseId));

        service.answer(confirmationId, "YES_IT_WAS_ME", "applicant-1");

        verify(cardAccounts, never()).freeze(any());
        verify(cases).insertLabel(eq(caseId), eq("LEGITIMATE"), eq("CUSTOMER_CONFIRM"), eq("applicant-1"), anyString());
    }

    @Test
    @DisplayName("an already-answered confirmation is a no-op, not an error")
    void alreadyAnsweredIsANoOp() {
        ConfirmationContext answered = new ConfirmationContext(confirmationId, transactionId, cardAccountId,
                applicationId, true);
        given(confirmations.findContext(confirmationId)).willReturn(Optional.of(answered));

        service.answer(confirmationId, "NOT_ME", "applicant-1");

        verify(confirmations, never()).recordAnswer(any(), anyString());
        verify(cardAccounts, never()).freeze(any());
    }

    @Test
    @DisplayName("an unknown confirmation id is rejected")
    void unknownConfirmationIsRejected() {
        given(confirmations.findContext(confirmationId)).willReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.answer(confirmationId, "NOT_ME", "actor"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("no case for the application means no label, but the answer still records and the card still freezes")
    void noCaseFoundStillFreezesButSkipsLabel() {
        given(confirmations.findContext(confirmationId)).willReturn(Optional.of(freshContext()));
        given(confirmations.recordAnswer(confirmationId, "NOT_ME")).willReturn(true);
        given(cases.findLatestCaseForApplication(applicationId)).willReturn(Optional.empty());

        service.answer(confirmationId, "NOT_ME", "applicant-1");

        verify(cardAccounts).freeze(cardAccountId);
        verify(cases, never()).insertLabel(any(), anyString(), anyString(), anyString(), anyString());
    }
}
