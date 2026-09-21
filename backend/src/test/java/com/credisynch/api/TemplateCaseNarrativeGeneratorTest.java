package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.credisynch.api.cases.CaseNarrativeGenerator.Context;
import com.credisynch.api.cases.CaseNarrativeGenerator.Narrative;
import com.credisynch.api.cases.CaseNarrativeGenerator.ReasonCodeView;
import com.credisynch.api.cases.TemplateCaseNarrativeGenerator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateCaseNarrativeGeneratorTest {

    private final TemplateCaseNarrativeGenerator generator = new TemplateCaseNarrativeGenerator();

    @Test
    @DisplayName("always produces a brief - the whole point of the degraded-mode template")
    void alwaysProducesABrief() {
        Context context = new Context(UUID.randomUUID(), "REVIEW", 0.5, List.of(), List.of());

        Optional<Narrative> narrative = generator.generate(context);

        assertThat(narrative).isPresent();
        assertThat(narrative.get().model()).isEqualTo("template-v1");
    }

    @Test
    @DisplayName("cites only the reason codes and linked-application count it was given")
    void citesOnlySuppliedEvidence() {
        UUID linkedApp = UUID.randomUUID();
        Context context = new Context(UUID.randomUUID(), "APPROVE_RESTRICTED", 0.02016,
                List.of(
                        new ReasonCodeView("VELOCITY_6H", "velocity_6h", 0.5, "INCREASES_RISK"),
                        new ReasonCodeView("ADDRESS_MONTHS", "current_address_months_count", -0.3, "DECREASES_RISK")),
                List.of(linkedApp));

        String brief = generator.generate(context).orElseThrow().brief();

        assertThat(brief).contains("APPROVE_RESTRICTED");
        assertThat(brief).contains("2.02%");
        assertThat(brief).contains("velocity_6h");
        assertThat(brief).contains("current_address_months_count");
        assertThat(brief).contains("1 other application");
        assertThat(brief).doesNotContain(linkedApp.toString());
    }

    @Test
    @DisplayName("says so plainly when there is no shared identity with another application")
    void noLinkedApplicationsIsStatedPlainly() {
        Context context = new Context(UUID.randomUUID(), "APPROVE", 0.001, List.of(), List.of());

        String brief = generator.generate(context).orElseThrow().brief();

        assertThat(brief).contains("No shared identities");
    }
}
