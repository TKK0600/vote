package com.example.vote.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRegisterReqDTO {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;
}
