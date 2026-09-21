package com.credisynch.api.decision;

import com.credisynch.api.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cost-based decision bands.
 *
 * With p = calibrated fraud probability, L = loss given fraud, r = share of fraudsters a step-up
 * stops, c_s = step-up friction cost, c_r = restricted-approval friction cost, e = share of the
 * loss still exposed under restrictions and C_d = cost of wrongly declining a good customer:
 *
 *   approve             p·L
 *   step-up             c_s + p(1-r)L
 *   approve-restricted  c_r + p·e·L
 *   decline             (1-p)·C_d
 *
 * Equating consecutive actions gives the thresholds below. At a ~1% fraud base rate a 0.5 cut-off
 * would be absurd; these thresholds come out orders of magnitude lower, which is the point.
 */
@Component
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private final AppProperties.Policy policy;
    private final double stepUpThreshold;
    private final double restrictedThreshold;
    private final double reviewThreshold;

    public PolicyEngine(AppProperties properties) {
        this.policy = properties.policy();
        double loss = policy.lossGivenFraud();
        double catchRate = policy.stepUpCatchRate();
        double exposure = policy.exposureFraction();

        this.stepUpThreshold = policy.stepUpCost() / (catchRate * loss);
        this.restrictedThreshold =
                (policy.restrictedCost() - policy.stepUpCost()) / ((1 - catchRate - exposure) * loss);
        this.reviewThreshold =
                (policy.declineCost() - policy.restrictedCost()) / (exposure * loss + policy.declineCost());
    }

    @PostConstruct
    void validateAndLog() {
        if (!(stepUpThreshold < restrictedThreshold && restrictedThreshold < reviewThreshold)) {
            throw new IllegalStateException(
                    "Policy cost parameters produce non-monotonic thresholds: "
                            + stepUpThreshold + ", " + restrictedThreshold + ", " + reviewThreshold);
        }
        log.info("Policy {} bands: approve < {} <= step-up < {} <= restricted < {} <= review",
                policy.version(), fmt(stepUpThreshold), fmt(restrictedThreshold), fmt(reviewThreshold));
    }

    private String fmt(double value) {
        return String.format("%.4f", value);
    }

    public record Bands(double stepUp, double restricted, double review) {}

    public Bands bands() {
        return new Bands(stepUpThreshold, restrictedThreshold, reviewThreshold);
    }

    public String version() {
        return policy.version();
    }

    /**
     * Chooses the cheapest action that contains the risk, then applies any rule floor.
     * A degraded score (model unavailable) never approves: it routes to a human.
     */
    public DecisionAction decide(double fraudProbability, boolean degraded, DecisionAction ruleFloor) {
        DecisionAction scoreAction;
        if (degraded || Double.isNaN(fraudProbability)) {
            scoreAction = DecisionAction.REVIEW;
        } else if (fraudProbability < stepUpThreshold) {
            scoreAction = DecisionAction.APPROVE;
        } else if (fraudProbability < restrictedThreshold) {
            scoreAction = DecisionAction.STEP_UP;
        } else if (fraudProbability < reviewThreshold) {
            scoreAction = DecisionAction.APPROVE_RESTRICTED;
        } else {
            scoreAction = DecisionAction.REVIEW;
        }
        if (ruleFloor == null) {
            return scoreAction;
        }
        return ruleFloor.ordinal() > scoreAction.ordinal() ? ruleFloor : scoreAction;
    }

    /**
     * Message shown to the applicant. Deliberately uninformative about model internals:
     * telling a fraudster which signal fired is free tuition (analysts see the full reasons).
     */
    public String customerMessage(DecisionAction action) {
        return switch (action) {
            case APPROVE -> "Your application has been approved.";
            case STEP_UP -> "Almost there - please complete a quick verification step to continue.";
            case APPROVE_RESTRICTED ->
                    "Your application has been approved with a starting limit while we finish setting up your account.";
            case REVIEW -> "Thanks - your application needs a short review. We will be in touch shortly.";
            case DECLINE -> "We are unable to approve this application at the moment.";
        };
    }
}
