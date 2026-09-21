package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.decision.DecisionAction;
import com.credisynch.api.decision.PolicyEngine;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The thresholds are derived, not guessed, so they are tested as arithmetic:
 * t_stepUp = c_s / (r·L), t_restricted = (c_r - c_s) / ((1-r-e)·L),
 * t_review = (C_d - c_r) / (e·L + C_d).
 */
class PolicyEngineTest {

    private static final double LOSS = 1500.0;

    private PolicyEngine engine() {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ml("http://localhost:8000", 400),
                new AppProperties.Hashing("test-key"),
                new AppProperties.Policy("policy-test", LOSS, 3.0, 0.8, 5.0, 0.1, 150.0),
                new AppProperties.Graph(720, 1, 3),
                new AppProperties.Restricted(50000, 3, 90, 0.45),
                new AppProperties.Bedrock("apac.amazon.nova-lite-v1:0", "amazon.titan-embed-text-v2:0"),
                new AppProperties.Llm("bedrock", null, "gemini-2.5-flash", "gemini-embedding-001"));
        return new PolicyEngine(properties);
    }

    @Test
    @DisplayName("bands match the closed-form thresholds")
    void bandsMatchTheDerivation() {
        PolicyEngine.Bands bands = engine().bands();
        assertThat(bands.stepUp()).isCloseTo(3.0 / (0.8 * LOSS), org.assertj.core.data.Offset.offset(1e-9));
        assertThat(bands.restricted()).isCloseTo((5.0 - 3.0) / (0.1 * LOSS), org.assertj.core.data.Offset.offset(1e-9));
        assertThat(bands.review()).isCloseTo(145.0 / (0.1 * LOSS + 150.0), org.assertj.core.data.Offset.offset(1e-9));
        assertThat(bands.stepUp()).isLessThan(bands.restricted());
        assertThat(bands.restricted()).isLessThan(bands.review());
    }

    @Test
    @DisplayName("each band selects the cheapest containing action")
    void bandsSelectActions() {
        PolicyEngine engine = engine();
        PolicyEngine.Bands bands = engine.bands();
        assertThat(engine.decide(0.0, false, null)).isEqualTo(DecisionAction.APPROVE);
        assertThat(engine.decide(bands.stepUp() - 1e-9, false, null)).isEqualTo(DecisionAction.APPROVE);
        assertThat(engine.decide(bands.stepUp(), false, null)).isEqualTo(DecisionAction.STEP_UP);
        assertThat(engine.decide(bands.restricted(), false, null)).isEqualTo(DecisionAction.APPROVE_RESTRICTED);
        assertThat(engine.decide(bands.review(), false, null)).isEqualTo(DecisionAction.REVIEW);
        assertThat(engine.decide(0.99, false, null)).isEqualTo(DecisionAction.REVIEW);
    }

    @Test
    @DisplayName("a 0.5 cut-off would approve almost every fraudster at this base rate")
    void thresholdsAreFarBelowAHalf() {
        assertThat(engine().bands().stepUp()).isLessThan(0.01);
    }

    @Test
    @DisplayName("a degraded score never approves; it routes to a human")
    void degradedScoreRoutesToReview() {
        assertThat(engine().decide(Double.NaN, true, null)).isEqualTo(DecisionAction.REVIEW);
        assertThat(engine().decide(0.0001, true, null)).isEqualTo(DecisionAction.REVIEW);
    }

    @Test
    @DisplayName("a rule floor can only make the decision stricter")
    void ruleFloorOnlyTightens() {
        PolicyEngine engine = engine();
        assertThat(engine.decide(0.0, false, DecisionAction.REVIEW)).isEqualTo(DecisionAction.REVIEW);
        assertThat(engine.decide(0.99, false, DecisionAction.STEP_UP)).isEqualTo(DecisionAction.REVIEW);
    }

    @Test
    @DisplayName("the customer message never leaks which signal fired")
    void customerMessagesAreNotExploitable() {
        PolicyEngine engine = engine();
        for (DecisionAction action : DecisionAction.values()) {
            String message = engine.customerMessage(action).toLowerCase();
            assertThat(message).doesNotContain("fraud", "model", "rule", "score", "device");
        }
    }
}
