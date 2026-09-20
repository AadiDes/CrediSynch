package com.credisynch.api.identity;

import com.credisynch.api.config.JwtRoleConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Identity", description = "Who the caller is, as the API sees them")
public class MeController {

    public record MeResponse(String username, List<String> roles, String authLevel, String issuer) {}

    @GetMapping("/me")
    @Operation(summary = "Return the caller's identity, roles and authentication level")
    public MeResponse me(Authentication authentication) {
        List<String> roles = JwtRoleConverter.roleNames(authentication.getAuthorities());
        String authLevel = "unknown";
        String issuer = "unknown";
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String acr = jwt.getClaimAsString("acr");
            authLevel = acr != null ? acr : "1";
            issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "unknown";
        }
        return new MeResponse(authentication.getName(), roles, authLevel, issuer);
    }
}
