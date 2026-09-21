package com.credisynch.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly typed application configuration. Secrets are injected from the environment. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Ml ml, Hashing hashing, Policy policy, Graph graph, Restricted restricted) {

    public record Cors(List<String> allowedOrigins) {}

    public record Ml(String baseUrl, int timeoutMs) {}

    public record Hashing(String entityKey) {}

    /**
     * Cost parameters for the decision policy. Thresholds are derived from these, so changing a
     * business assumption changes the decision bands - no magic numbers in code.
     */
    public record Policy(
            String version,
            double lossGivenFraud,
            double stepUpCost,
            double stepUpCatchRate,
            double restrictedCost,
            double exposureFraction,
            double declineCost) {}

    /**
     * Graph linkage stage (pipeline stage 3): how many other applications share a device, phone,
     * email, address or bank account with this one, and at what count that shared identity alone
     * becomes strict enough to floor the decision.
     */
    public record Graph(
            int linkWindowHours,
            int stepUpLinkedThreshold,
            int reviewLinkedThreshold) {}

    /**
     * Module A (ADR 0007): the card issued on an APPROVE_RESTRICTED decision, and how confident a
     * merchant-descriptor match has to be before it is trusted without asking the customer.
     */
    public record Restricted(
            long startingLimitMinor,
            int velocityCapPerDay,
            int liftAfterDays,
            double matchConfidentThreshold) {}
}
