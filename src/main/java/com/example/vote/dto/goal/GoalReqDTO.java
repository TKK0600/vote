package com.example.vote.dto.goal;

import com.example.vote.modal.quest.Goal;
import com.example.vote.modal.quest.Mission;

import java.util.List;

public record GoalReqDTO(
        List<Goal> goals,
        List<Mission> missions
) {
}
