package com.credisynch.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CrediSynchApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrediSynchApplication.class, args);
    }
}
