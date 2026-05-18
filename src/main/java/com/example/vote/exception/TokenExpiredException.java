package com.example.vote.exception;

public class TokenExpiredException extends BusinessException {
    public TokenExpiredException(String message) {
        super("TOKEN_EXPIRED", message);
    }
}
