package com.teammemory.mcp.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    private final ApiKeyHasher hasher = new ApiKeyHasher();

    @Test
    void generatedTokenHasExpectedPrefixAndLength() {
        String token = hasher.generateToken();

        assertThat(token).startsWith("tmk_");
        // "tmk_" + base64url(32 bytes, no padding) = 4 + 43 chars
        assertThat(token).hasSize(47);
    }

    @Test
    void hashingTheSameTokenTwiceProducesTheSameHash() {
        String token = hasher.generateToken();

        assertThat(hasher.hash(token)).isEqualTo(hasher.hash(token));
    }

    @Test
    void differentTokensHashDifferently() {
        String tokenA = hasher.generateToken();
        String tokenB = hasher.generateToken();

        assertThat(hasher.hash(tokenA)).isNotEqualTo(hasher.hash(tokenB));
    }

    @Test
    void hashIsHexEncodedSha256() {
        String hash = hasher.hash("tmk_example");

        // SHA-256 -> 32 bytes -> 64 hex characters
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void generatedTokensDoNotCollideAcrossManyCalls() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            tokens.add(hasher.generateToken());
        }

        assertThat(tokens).hasSize(10_000);
    }

    @Test
    void prefixOfReturnsTheFirstTwelveCharacters() {
        String token = "tmk_abcdefghijklmnopqrstuvwxyz";

        assertThat(hasher.prefixOf(token)).isEqualTo("tmk_abcdefgh").hasSize(12);
    }
}
