package com.example.vote.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey",
                "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256");
    }

    @Test
    @DisplayName("Should generate valid access token and extract email")
    void shouldGenerateAndExtractEmail() {
        String token = jwtUtil.generateAccessToken("user@example.com", 1L);

        assertNotNull(token);
        assertEquals("user@example.com", jwtUtil.extractUserEmail(token));
    }

    @Test
    @DisplayName("Should extract user ID from token")
    void shouldExtractUserId() {
        String token = jwtUtil.generateAccessToken("user@example.com", 42L);

        assertEquals(42L, jwtUtil.extractUserId(token));
    }

    @Test
    @DisplayName("Should return userId correctly for various values")
    void shouldExtractUserIdCorrectly() {
        String token = jwtUtil.generateAccessToken("user@example.com", 100L);
        assertEquals(100L, jwtUtil.extractUserId(token));
    }

    @Test
    @DisplayName("Should produce different tokens for different users")
    void shouldProduceDifferentTokensForDifferentUsers() {
        String token1 = jwtUtil.generateAccessToken("user1@example.com", 1L);
        String token2 = jwtUtil.generateAccessToken("user2@example.com", 2L);

        assertNotEquals(token1, token2);
        assertEquals("user1@example.com", jwtUtil.extractUserEmail(token1));
        assertEquals("user2@example.com", jwtUtil.extractUserEmail(token2));
    }

    @Test
    @DisplayName("Should produce valid tokens with correct structure")
    void shouldProduceValidTokens() {
        String token1 = jwtUtil.generateAccessToken("user@example.com", 1L);
        // Token should be a non-empty String with 3 dot-separated parts (header.payload.signature)
        assertNotNull(token1);
        assertEquals(3, token1.split("\\.").length);
    }
}
