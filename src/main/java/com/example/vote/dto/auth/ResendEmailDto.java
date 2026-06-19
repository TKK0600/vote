package com.example.vote.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendEmailDto {
    @Email
    @NotBlank
    private String email;
}
