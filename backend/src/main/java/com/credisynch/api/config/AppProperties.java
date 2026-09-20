package com.credisynch.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly typed application configuration; nothing here is a secret. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Ml ml) {

    public record Cors(List<String> allowedOrigins) {}

    public record Ml(String baseUrl) {}
}
