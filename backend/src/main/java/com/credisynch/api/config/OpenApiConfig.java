package com.credisynch.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI crediSynchOpenApi() {
        final String scheme = "bearer-jwt";
        return new OpenAPI()
                .info(new Info()
                        .title("CrediSynch Decision API")
                        .version("v1")
                        .description("Real-time fraud decisioning for digital lending. "
                                + "Hand-written contract lives in /api/openapi.yaml; this is the runtime view."))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
