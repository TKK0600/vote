package com.example.vote.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class GoogleTokenVerifierService {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    private final JwtDecoder googleJwtDecoder;

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    public GoogleTokenVerifierService() {
        this.googleJwtDecoder = NimbusJwtDecoder
                .withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .build();
    }

    public GoogleUserInfo verify(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new IllegalArgumentException("Google id token is required");
        }

        if (!StringUtils.hasText(googleClientId)) {
            throw new IllegalStateException("Google OAuth client id is not configured");
        }

        Jwt jwt;
        try {
            jwt = googleJwtDecoder.decode(idToken);
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid Google id token");
        }

        validateIssuer(jwt.getIssuer() == null ? null : jwt.getIssuer().toString());
        validateAudience(jwt.getAudience());
        validateEmailVerified(jwt.getClaims());

        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Google token does not contain email");
        }

        return new GoogleUserInfo(jwt.getSubject(), email, jwt.getClaimAsString("name"));
    }

    private void validateIssuer(String issuer) {
        if (!StringUtils.hasText(issuer) ||
                !(GOOGLE_ISSUER.equals(issuer) || "accounts.google.com".equals(issuer))) {
            throw new IllegalArgumentException("Invalid Google token issuer");
        }
    }

    private void validateAudience(List<String> audience) {
        if (audience == null || !audience.contains(googleClientId)) {
            throw new IllegalArgumentException("Google token audience does not match configured client id");
        }
    }

    private void validateEmailVerified(Map<String, Object> claims) {
        Object emailVerified = claims.get("email_verified");
        if (!(emailVerified instanceof Boolean verified) || !verified) {
            throw new IllegalArgumentException("Google account email is not verified");
        }
    }

    public record GoogleUserInfo(String providerUserId, String email, String name) {
    }
}
