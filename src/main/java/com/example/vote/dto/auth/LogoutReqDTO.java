package com.example.vote.dto.auth;

import lombok.Data;

@Data
public class LogoutReqDTO {
    private String refreshToken;
}
