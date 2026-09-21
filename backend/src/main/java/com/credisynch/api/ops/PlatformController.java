package com.credisynch.api.ops;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role-gated endpoints. These exist from day one so that authorisation is proven
 * by tests and in the demo, not asserted on a slide.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Platform", description = "Role-gated platform operations")
public class PlatformController {

    public record PlatformStatus(String service, String status, Instant checkedAt) {}

    @GetMapping("/platform/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin-only: platform status")
    public PlatformStatus platformStatus() {
        return new PlatformStatus("credisynch-api", "UP", Instant.now());
    }
}
