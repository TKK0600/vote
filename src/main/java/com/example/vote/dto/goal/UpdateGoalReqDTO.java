package com.example.vote.dto.goal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateGoalReqDTO(
        @NotNull Long id,
        @Min(1) @Max(52) Integer weekRequired
) {}
