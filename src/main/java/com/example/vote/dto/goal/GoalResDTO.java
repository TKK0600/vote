package com.example.vote.dto.goal;

import java.time.LocalDateTime;

public record GoalResDTO(
        Long id,
        String title,
        String status,
        String category,
        LocalDateTime createdAt
) {}