package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.credisynch.api.config.JwtRoleConverter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    private Jwt jwtWith(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("f:1234:analyst")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    @DisplayName("keycloak realm roles become ROLE_ authorities")
    void mapsRealmRoles() {
        Jwt jwt = jwtWith(Map.of(
                "preferred_username", "analyst",
                "realm_access", Map.of("roles", List.of("ANALYST", "APPLICANT"))));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getName()).isEqualTo("analyst");
        assertThat(token.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactlyInAnyOrder("ROLE_ANALYST", "ROLE_APPLICANT");
    }

    @Test
    @DisplayName("a token with no realm roles gets no authorities rather than failing open")
    void missingRolesYieldNoAuthorities() {
        AbstractAuthenticationToken token = converter.convert(jwtWith(Map.of("preferred_username", "nobody")));
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("subject is used when preferred_username is absent")
    void fallsBackToSubject() {
        AbstractAuthenticationToken token = converter.convert(jwtWith(Map.of(
                "realm_access", Map.of("roles", List.of("ADMIN")))));
        assertThat(token.getName()).isEqualTo("f:1234:analyst");
    }
}
