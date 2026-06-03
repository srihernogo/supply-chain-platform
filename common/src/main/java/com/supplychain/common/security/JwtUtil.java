package com.supplychain.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Stateless JWT utility — used by both API Gateway (token generation)
 * and all downstream services (token validation).
 *
 * <p>Algorithm: HS256 with a shared secret key (min 256 bits / 32 chars).
 * <p>Claims structure:
 * <pre>
 * {
 *   "sub":      "warehouse@toyota",
 *   "tenantId": "toyota",
 *   "role":     "WAREHOUSE",
 *   "iat":      1717000000,
 *   "exp":      1717086400
 * }
 * </pre>
 */
public final class JwtUtil {

    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_ROLE      = "role";

    private final SecretKey secretKey;
    private final long      expirationMs;

    public JwtUtil(String secret, long expirationMs) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.secretKey    = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // ─────────────────────────────────────────────────
    // Token Generation
    // ─────────────────────────────────────────────────

    /**
     * Generate a signed JWT token.
     *
     * @param subject  typically username/email
     * @param tenantId the tenant the user belongs to
     * @param role     one of: ADMIN, WAREHOUSE, PRODUCTION, AUDITOR, SUPPLIER
     * @return compact JWT string
     */
    public String generateToken(String subject, String tenantId, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(subject)
                .claims(Map.of(
                        CLAIM_TENANT_ID, tenantId,
                        CLAIM_ROLE, role
                ))
                .issuedAt(now)
                .expiration(exp)
                .signWith(secretKey)
                .compact();
    }

    // ─────────────────────────────────────────────────
    // Token Validation & Parsing
    // ─────────────────────────────────────────────────

    /**
     * Parse and validate a JWT token.
     *
     * @param token the Bearer token (without "Bearer " prefix)
     * @return Claims if valid
     * @throws JwtException if invalid or expired
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validate token and return true if valid, false otherwise.
     */
    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getSubject(String token) {
        return parseToken(token).getSubject();
    }

    public String getTenantId(String token) {
        return parseToken(token).get(CLAIM_TENANT_ID, String.class);
    }

    public String getRole(String token) {
        return parseToken(token).get(CLAIM_ROLE, String.class);
    }
}
