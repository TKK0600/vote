package com.example.vote.exception;

public class OAuth2AuthenticationException extends BusinessException {
    public OAuth2AuthenticationException(String message) {
        super("OAUTH2_AUTH_FAILED", message);
    }
}
