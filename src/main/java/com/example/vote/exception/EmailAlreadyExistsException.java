package com.example.vote.exception;

public class EmailAlreadyExistsException extends BusinessException {
    public EmailAlreadyExistsException(String message) {
        super("EMAIL_ALREADY_EXISTS", message);
    }
}
