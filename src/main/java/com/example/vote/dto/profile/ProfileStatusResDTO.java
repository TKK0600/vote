package com.example.vote.dto.profile;

public record ProfileStatusResDTO(
    boolean scheduleComplete,
    boolean obstacleComplete,
    boolean dailyStructureComplete,
    boolean aiProfileGenerated,
    boolean profileComplete
) {}
