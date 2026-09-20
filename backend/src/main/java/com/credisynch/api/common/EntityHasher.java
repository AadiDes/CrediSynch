package com.credisynch.api.common;

import com.credisynch.api.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Keyed one-way digest for identifiers used in graph linkage (ADR 0006).
 * Ring detection only needs to know that two applications share a value, never what it is,
 * so devices, phones, emails and addresses are stored as HMAC-SHA256 digests.
 */
@Component
public class EntityHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] key;

    public EntityHasher(AppProperties properties) {
        this.key = properties.hashing().entityKey().getBytes(StandardCharsets.UTF_8);
    }

    /** Normalises then hashes; normalisation makes "+91 98765 43210" and "9876543210" match. */
    public String hash(String type, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalised = normalise(type, rawValue);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal((type + "|" + normalised).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash entity value", e);
        }
    }

    private String normalise(String type, String value) {
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "PHONE" -> trimmed.replaceAll("[^0-9]", "").replaceFirst("^(0|91)(?=\\d{10}$)", "");
            case "EMAIL" -> trimmed.replaceAll("\\s", "");
            case "ADDRESS" -> trimmed.replaceAll("[^a-z0-9]", "");
            default -> trimmed;
        };
    }
}
