package com.credisynch.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.config.SecurityConfig;
import com.credisynch.api.identity.MeController;
import com.credisynch.api.ops.PlatformController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Authorisation is a contract, so it is tested like one:
 * anonymous is rejected, and each role only reaches its own endpoints.
 */
@WebMvcTest(controllers = {MeController.class, PlatformController.class},
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AppProperties.class)
class SecurityRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("anonymous callers are rejected")
    void anonymousIsUnauthorised() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("any authenticated caller can read their own identity")
    void authenticatedCanReadIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(j -> j.claim("preferred_username", "applicant"))
                                .authorities(new SimpleGrantedAuthority("ROLE_APPLICANT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("APPLICANT"));
    }

    @Test
    @DisplayName("an applicant cannot open the analyst queue")
    void applicantCannotReachAnalystQueue() throws Exception {
        mockMvc.perform(get("/api/v1/queue/summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_APPLICANT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an analyst can open the analyst queue")
    void analystCanReachAnalystQueue() throws Exception {
        mockMvc.perform(get("/api/v1/queue/summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an analyst cannot reach admin-only platform status")
    void analystCannotReachAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/platform/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin can reach admin-only platform status")
    void adminCanReachAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/platform/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
