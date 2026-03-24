package com.willa.ai.backend.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.jwtSecret=testSecretKeyForJwtTokenGenerationAndValidation12345678901234567890",
        "app.jwtAccessTokenExpiry=86400000",
        "app.jwtRefreshTokenExpiry=604800000"
})
class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider tokenProvider;

    private String testEmail = "test@example.com";
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        assertNotNull(tokenProvider);
    }

    @Test
    void testGenerateAccessToken() {
        String token = tokenProvider.generateAccessToken(testEmail, testUserId);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testGenerateRefreshToken() {
        String token = tokenProvider.generateRefreshToken(testEmail, testUserId);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testValidateToken() {
        String token = tokenProvider.generateAccessToken(testEmail, testUserId);
        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void testGetEmailFromToken() {
        String token = tokenProvider.generateAccessToken(testEmail, testUserId);
        String email = tokenProvider.getEmailFromToken(token);
        assertEquals(testEmail, email);
    }

    @Test
    void testGetUserIdFromToken() {
        String token = tokenProvider.generateAccessToken(testEmail, testUserId);
        Long userId = tokenProvider.getUserIdFromToken(token);
        assertEquals(testUserId, userId);
    }

    @Test
    void testParseToken() {
        String token = tokenProvider.generateAccessToken(testEmail, testUserId);
        Claims claims = tokenProvider.parseToken(token);
        assertNotNull(claims);
        assertEquals(testEmail, claims.getSubject());
    }

    @Test
    void testInvalidToken() {
        String invalidToken = "invalid.token.here";
        assertFalse(tokenProvider.validateToken(invalidToken));
    }
}
