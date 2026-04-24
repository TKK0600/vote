package com.example.vote.vo.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegistrationVo {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;
}
