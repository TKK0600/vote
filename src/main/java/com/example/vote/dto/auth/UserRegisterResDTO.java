package com.example.vote.dto.auth;

import lombok.Data;

@Data
public class UserRegisterResDTO {
    private Long userId;
    private String email;

    public UserRegisterResDTO(Long id, String email) {
        this.userId = id;
        this.email = email;
    }

    public UserRegisterResDTO() {

    }
}
