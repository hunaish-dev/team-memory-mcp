package com.teammemory.mcp.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token generation and hashing for API keys. Uses a fast cryptographic hash
 * (SHA-256), not an adaptive password hash (bcrypt/argon2) — the token is a
 * 256-bit {@link SecureRandom} value, not a human-chosen password, so there's
 * nothing low-entropy to defend against by slowing the hash down. Adaptive
 * hashing would just tax every single MCP request for no security benefit.
 */
@Component
public class ApiKeyHasher {

    private static final String TOKEN_PREFIX = "tmk_";
    private static final int TOKEN_RANDOM_BYTES = 32;
    private static final int PREFIX_DISPLAY_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateToken() {
        byte[] bytes = new byte[TOKEN_RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a JLS-mandated algorithm, guaranteed present on every JVM.
            throw new IllegalStateException(e);
        }
    }

    public String prefixOf(String token) {
        return token.substring(0, Math.min(PREFIX_DISPLAY_LENGTH, token.length()));
    }
}
