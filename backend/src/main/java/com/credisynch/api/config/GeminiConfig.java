package com.credisynch.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Only created when app.llm.provider=gemini; the Bedrock path needs no equivalent HTTP client. */
@Configuration
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "gemini")
public class GeminiConfig {

    @Bean
    RestClient geminiRestClient() {
        return RestClient.builder().baseUrl("https://generativelanguage.googleapis.com").build();
    }
}
