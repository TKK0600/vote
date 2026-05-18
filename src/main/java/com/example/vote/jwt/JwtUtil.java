package com.example.vote.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private static final long ACCESS_TOKEN_EXPIRY_MS = 1000L * 60 * 15; // 15 minutes

    @Value("${token.jwt.secret}")
    private String secretKey;

    public String generateAccessToken(String email, Long userId) {
        return Jwts.builder()
                .setSubject(email)
                .addClaims(Map.of("uid", userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY_MS))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUserEmail(String token) {
        return parseToken(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object uid = parseToken(token).get("uid");

        if (uid instanceof Integer integerUid) {
            return integerUid.longValue();
        }
        if (uid instanceof Long longUid) {
            return longUid;
        }
        if (uid instanceof String stringUid) {
            return Long.parseLong(stringUid);
        }
        return null;
    }

    private Claims parseToken(String token) {
        return createParser().parseClaimsJws(token).getBody();
    }

    private JwtParser createParser() {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
