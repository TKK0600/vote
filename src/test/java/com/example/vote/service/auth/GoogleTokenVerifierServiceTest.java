package com.example.vote.service.auth;

import com.example.vote.exception.OAuth2AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoogleTokenVerifierServiceTest {

    private GoogleTokenVerifierService verifierService;

    @BeforeEach
    void setUp() {
        verifierService = new GoogleTokenVerifierService("test-client-id.apps.googleusercontent.com");
    }

    @Test
    @DisplayName("Should throw OAuth2AuthenticationException when id token is empty")
    void shouldThrowForEmptyIdToken() {
        assertThrows(OAuth2AuthenticationException.class, () -> verifierService.verify(""));
        assertThrows(OAuth2AuthenticationException.class, () -> verifierService.verify(null));
    }

    @Test
    @DisplayName("Should throw when client ID is not configured")
    void shouldThrowWhenClientIdMissing() {
        GoogleTokenVerifierService service = new GoogleTokenVerifierService("");

        assertThrows(OAuth2AuthenticationException.class, () -> service.verify("some-token"));
    }

    @Test
    @DisplayName("Should throw for malformed JWT token")
    void shouldThrowForMalformedToken() {
        assertThrows(OAuth2AuthenticationException.class,
                () -> verifierService.verify("not-a-valid-jwt"));
    }

    @Test
    @DisplayName("Should throw when client ID is blank")
    void shouldThrowWhenClientIdIsBlank() {
        GoogleTokenVerifierService service = new GoogleTokenVerifierService("   ");

        assertThrows(OAuth2AuthenticationException.class, () -> service.verify("some.jwt.token"));
    }
}
