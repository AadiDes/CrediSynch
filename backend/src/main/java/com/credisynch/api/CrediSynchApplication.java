package com.credisynch.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/** @EnableAsync: case embedding generation stays off the synchronous decision path (docs/architecture.md). */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class CrediSynchApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrediSynchApplication.class, args);
    }
}
