package com.example.vote.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationStatusResDto {
    private boolean isVerified;
    private AuthResponse authResponse;
    private String message;
}
