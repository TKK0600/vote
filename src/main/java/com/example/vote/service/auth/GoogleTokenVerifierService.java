package com.example.vote.service.auth;

import com.example.vote.exception.OAuth2AuthenticationException;
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
    private static final String GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";

    private final JwtDecoder googleJwtDecoder;
    private final String googleClientId;

    public GoogleTokenVerifierService(@Value("${google.oauth.client-id:}") String googleClientId) {
        this.googleClientId = googleClientId;
        this.googleJwtDecoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_CERTS_URL).build();
    }

    public GoogleUserInfo verify(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new OAuth2AuthenticationException("Google ID token is required");
        }

        if (!StringUtils.hasText(googleClientId)) {
            throw new OAuth2AuthenticationException("Google OAuth client ID is not configured");
        }

        Jwt jwt = decodeToken(idToken);
        validateTokenClaims(jwt);

        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(email)) {
            throw new OAuth2AuthenticationException("Google token does not contain email");
        }

        return new GoogleUserInfo(jwt.getSubject(), email, jwt.getClaimAsString("name"));
    }

    private Jwt decodeToken(String idToken) {
        try {
            return googleJwtDecoder.decode(idToken);
        } catch (JwtException e) {
            throw new OAuth2AuthenticationException("Invalid Google ID token");
        }
    }

    private void validateTokenClaims(Jwt jwt) {
        validateIssuer(jwt.getIssuer() != null ? jwt.getIssuer().toString() : null);
        validateAudience(jwt.getAudience());
        validateEmailVerified(jwt.getClaims());
    }

    private void validateIssuer(String issuer) {
        if (!StringUtils.hasText(issuer)
                || !(GOOGLE_ISSUER.equals(issuer) || "accounts.google.com".equals(issuer))) {
            throw new OAuth2AuthenticationException("Invalid Google token issuer");
        }
    }

    private void validateAudience(List<String> audience) {
        if (audience == null || !audience.contains(googleClientId)) {
            throw new OAuth2AuthenticationException("Google token audience does not match configured client ID");
        }
    }

    private void validateEmailVerified(Map<String, Object> claims) {
        Object emailVerified = claims.get("email_verified");
        if (!(emailVerified instanceof Boolean verified) || !verified) {
            throw new OAuth2AuthenticationException("Google account email is not verified");
        }
    }

    public record GoogleUserInfo(String providerUserId, String email, String name) {}
}
