package com.example.vote.dto.auth;

import lombok.Data;

@Data
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";

    private String username;
    private Long expiryDuration;

    public AuthResponse(String accessToken, String token) {
        this.accessToken = accessToken;
        this.refreshToken = token;
    }
}
