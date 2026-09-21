package com.credisynch.api.decision;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Inbound application. Every field is validated before any model or database work happens. */
public record ApplicationRequest(
        @NotBlank @Size(max = 64) String externalRef,
        @NotNull @Pattern(regexp = "WEB|MOBILE|POS_PARTNER") String channel,
        @Size(max = 64) String partnerId,
        @NotNull @Valid Applicant applicant,
        @NotNull @Valid DeviceContext device,
        Map<String, Object> features,
        @Size(max = 500) String freeText) {

    public record Applicant(
            @NotBlank @Size(max = 128) String fullName,
            @NotBlank @Email @Size(max = 128) String email,
            @NotBlank @Size(max = 20) String phone,
            @Size(max = 200) String addressLine,
            @Size(max = 64) String bankAccountRef) {}

    public record DeviceContext(
            @NotBlank @Size(max = 128) String deviceFingerprint,
            @Size(max = 45) String ipAddress,
            Double sessionLengthMinutes,
            Boolean keepAliveSession) {}

    public Map<String, Object> featuresOrEmpty() {
        return features == null ? Map.of() : features;
    }
}
