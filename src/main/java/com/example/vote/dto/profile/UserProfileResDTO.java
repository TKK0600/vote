package com.example.vote.dto.profile;

import com.example.vote.modal.user.User.ActivityLevel;
import com.example.vote.modal.user.User.DailyStructure;
import com.example.vote.modal.user.User.EnergyPeak;
import com.example.vote.modal.user.User.Gender;
import com.example.vote.modal.user.User.Occupations;
import com.example.vote.modal.user.User.WeekendAvailability;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record UserProfileResDTO(
    // Demographics
    Integer age,
    Gender gender,
    Integer height,
    Integer weight,

    // Schedule
    LocalTime sleepTime,
    LocalTime wakeTime,

    // Lifestyle
    ActivityLevel activityLevel,
    Occupations occupation,

    // Daily structure & availability
    DailyStructure dailyStructure,
    String dailyStructureCustom,
    String blockedTime,
    String freeTimeWindows,
    EnergyPeak energyPeak,
    WeekendAvailability weekendAvailability,

    // Obstacles
    String mainObstacle,
    String environmentNotes,

    // System flags (read-only)
    boolean profileComplete,
    boolean scheduleComplete,
    boolean obstacleComplete,
    boolean aiProfileGenerated,
    LocalDateTime llmProfileUpdatedAt
) {}
