package com.example.vote.dto.goal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateGoalReqDTO(
        @NotBlank String title,
        String category,
        @NotNull @Min(1) @Max(52) int weekRequired
) {}