package com.example.vote.dto.auth;

import lombok.Data;

@Data
public class LoginReqDTO {
    private String email;
    private String password;
}
