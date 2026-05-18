package com.example.vote.exception;

public class TokenInactiveException extends BusinessException {
    public TokenInactiveException(String message) {
        super("TOKEN_INACTIVE", message);
    }
}
