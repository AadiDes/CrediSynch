package com.credisynch.api.cases;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** Default {@link CaseNarrativeGenerator}: no provider is configured, so every case brief stays null. */
@Component
public class NullCaseNarrativeGenerator implements CaseNarrativeGenerator {

    @Override
    public Optional<Narrative> generate(Context context) {
        return Optional.empty();
    }
}
