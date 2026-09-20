package com.credisynch.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly typed application configuration. Secrets are injected from the environment. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Ml ml, Hashing hashing, Policy policy) {

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
}
