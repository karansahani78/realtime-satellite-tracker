package com.sattrack.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

/**
 * JWT generation and validation.
 *
 * WHY HS256 instead of RS256?
 * - HS256 is simpler to deploy (no key pair management)
 * - RS256 is better when JWTs need to be verified by third parties
 * - For a self-contained platform, HS256 with a strong secret is sufficient
 * - Secret is 256-bit minimum to satisfy HS256 security requirements
 *
 * Token claims include role to avoid a DB lookup on every request.
 * If roles change, existing tokens are valid until expiry (acceptable for
 * this use case; for stricter revocation, add a token blacklist in Redis).
 */
@Service
@Slf4j
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expiration-ms:86400000}") long expirationMs) {
        // Keys.hmacShaKeyFor requires >= 256-bit (32 bytes) for HS256
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claims(Map.of("role", role))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
