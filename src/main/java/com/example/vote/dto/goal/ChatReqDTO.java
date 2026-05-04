package com.example.vote.dto.goal;

import jakarta.validation.constraints.NotBlank;

public record ChatReqDTO(
        @NotBlank String answer   // user's answer to the AI's question
) {}
