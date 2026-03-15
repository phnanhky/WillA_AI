package com.willa.ai.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtAccessTokenExpiry:86400000}")
    private long jwtAccessTokenExpiry;

    @Value("${app.jwtRefreshTokenExpiry:604800000}")
    private long jwtRefreshTokenExpiry;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String email, Long userId) {
        return generateToken(email, userId, jwtAccessTokenExpiry, "access");
    }

    public String generateRefreshToken(String email, Long userId) {
        return generateToken(email, userId, jwtRefreshTokenExpiry, "refresh");
    }

    public String generateToken(String email, Long userId, long expiryTime, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiryTime);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("tokenType", tokenType)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getEmailFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.error("Error getting email from token", e);
            return null;
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            log.error("Error getting userId from token", e);
            return null;
        }
    }

    public String getTokenTypeFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("tokenType", String.class);
        } catch (Exception e) {
            log.error("Error getting tokenType from token", e);
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpiry() {
        return jwtAccessTokenExpiry;
    }

    public long getRefreshTokenExpiry() {
        return jwtRefreshTokenExpiry;
    }
}
