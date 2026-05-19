package com.example.vote.dto.goal;

import com.example.vote.modal.quest.Goal;

public record MissionResDTO(
        Long id,
        Long goalId,
        String title,
        String description,
        String difficulty,
        int xpReward
) {}
