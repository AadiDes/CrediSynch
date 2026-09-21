package com.credisynch.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Credentials and region come from the default provider chain - the EC2 instance profile and
 * instance metadata service in every deployed environment, ~/.aws locally. No secret is ever a
 * literal here, matching the rest of the app's configuration.
 */
@Configuration
public class BedrockConfig {

    @Bean
    BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.create();
    }
}
