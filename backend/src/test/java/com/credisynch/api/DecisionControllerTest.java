package com.credisynch.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.config.SecurityConfig;
import com.credisynch.api.decision.DecisionAction;
import com.credisynch.api.decision.DecisionController;
import com.credisynch.api.decision.DecisionResponse;
import com.credisynch.api.decision.DecisionService;
import com.credisynch.api.decision.RingDetectionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DecisionController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AppProperties.class)
class DecisionControllerTest {

    private static final String VALID_BODY = """
            {
              "externalRef": "ref-001",
              "channel": "WEB",
              "partnerId": "partner-1",
              "applicant": {
                "fullName": "Asha Rao",
                "email": "asha@example.com",
                "phone": "9876543210",
                "addressLine": "12 MG Road",
                "bankAccountRef": "acct-1"
              },
              "device": { "deviceFingerprint": "device-1", "ipAddress": "10.0.0.1" },
              "features": { "customer_age": 34 }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DecisionService decisionService;

    @MockitoBean
    private RingDetectionService ringDetection;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private DecisionResponse approved() {
        return new DecisionResponse(UUID.randomUUID(), UUID.randomUUID(), DecisionAction.APPROVE,
                "Your application has been approved.", 0.0012, null, 0.2, List.of(), List.of(),
                "model-1", "policy-1.0.0", false, 42, Instant.now());
    }

    @Test
    @DisplayName("a valid submission returns a decision")
    void validSubmissionIsDecided() throws Exception {
        given(decisionService.decide(any(), anyString(), anyString())).willReturn(approved());

        mockMvc.perform(post("/api/v1/applications")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_APPLICANT")))
                        .header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("APPROVE"))
                .andExpect(jsonPath("$.customerMessage").exists());
    }

    @Test
    @DisplayName("a submission without an idempotency key is rejected")
    void missingIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_APPLICANT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an invalid email is rejected before any model work happens")
    void invalidPayloadIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_APPLICANT")))
                        .header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("asha@example.com", "not-an-email")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("anonymous submissions are rejected")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }
}
