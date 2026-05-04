package com.example.vote.dto.goal;

import jakarta.validation.constraints.NotBlank;

public record CreateGoalReqDTO(
        @NotBlank String title,
        String category
) {}