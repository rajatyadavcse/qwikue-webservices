package com.microservice.LoginService.security;

import com.microservice.LoginService.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiry;

    // ── Token Generation ──────────────────────────────────────────────────────

    public String generateAccessToken(String username, Role role, Long restaurantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role.name());
        claims.put("restaurantId", restaurantId);
        claims.put("type", "access");
        return buildToken(claims, username, accessTokenExpiry);
    }

    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return buildToken(claims, username, refreshTokenExpiry);
    }

    private String buildToken(Map<String, Object> claims, String subject, long expiry) {
        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(subject)
                .id(UUID.randomUUID().toString())   // unique JTI — used for blacklisting on logout
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .and()
                .signWith(getKey())
                .compact();
    }

    // ── Token Extraction ──────────────────────────────────────────────────────

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Instant extractExpiryAsInstant(String token) {
        return extractClaim(token, claims -> claims.getExpiration().toInstant());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Long extractRestaurantId(String token) {
        return extractClaim(token, claims -> {
            Object val = claims.get("restaurantId");
            if (val == null) return null;
            if (val instanceof Long l) return l;
            if (val instanceof Integer i) return i.longValue();
            return null;
        });
    }

    // ── Token Validation ──────────────────────────────────────────────────────

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
