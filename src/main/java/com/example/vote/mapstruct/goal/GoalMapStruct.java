package com.example.vote.mapstruct.goal;

import com.example.vote.config.MapStructConfig;
import com.example.vote.dto.goal.GoalResDTO;
import com.example.vote.dto.goal.MissionResDTO;
import com.example.vote.modal.quest.Goal;
import com.example.vote.modal.quest.Mission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", config = MapStructConfig.class)
public interface GoalMapStruct {

    @Mapping(target = "goalId", source = "goal.id")
    MissionResDTO toMissionResDTO(Mission mission);

    @Mapping(target = "status", expression = "java(goal.getStatus().name())")
    GoalResDTO toGoalResDTO(Goal goal);
}
