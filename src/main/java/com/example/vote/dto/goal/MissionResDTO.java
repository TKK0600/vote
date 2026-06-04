package com.example.vote.dto.goal;

import com.example.vote.modal.quest.Goal;
import com.example.vote.modal.quest.MissionStatus;

import java.time.LocalDate;

public record MissionResDTO(
        Long id,
        Long goalId,
        String title,
        String description,
        String difficulty,
        int xpReward,
        int weekNumber,
        LocalDate targetDate,
        MissionStatus status
) {}
