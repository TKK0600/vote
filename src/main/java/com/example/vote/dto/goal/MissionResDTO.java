package com.example.vote.dto.goal;

public record MissionResDTO(
        Long id,
        String title,
        String description,
        String difficulty,
        int xpReward
) {}
