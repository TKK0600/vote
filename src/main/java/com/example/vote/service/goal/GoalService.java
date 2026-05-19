package com.example.vote.service.goal;

import com.example.vote.dto.goal.GoalReqDTO;
import com.example.vote.modal.quest.Goal;
import com.example.vote.modal.quest.GoalStatus;
import com.example.vote.modal.quest.Mission;
import com.example.vote.modal.quest.MissionStatus;
import com.example.vote.repository.goal.GoalRepository;
import com.example.vote.repository.goal.MissionRepository;
import com.example.vote.util.RequestUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalService {
    private final GoalRepository goalRepository;
    private final MissionRepository missionRepository;

    @Transactional
    public ResponseEntity<Object> updateGoalStatus(GoalReqDTO reqDTO){
        Long currentUserId = RequestUserUtil.getCurrentUserId();

        if (reqDTO.goals() == null || reqDTO.goals().isEmpty()) {
            return ResponseEntity.badRequest().body("Goals list cannot be empty");
        }

        List<Goal> goalsToUpdate = new ArrayList<>();

        try {
            for (Goal updateGoal : reqDTO.goals()) {
                Goal goal = goalRepository.findById(updateGoal.getId())
                        .orElseThrow(() -> new RuntimeException("Goal not found with id: " + updateGoal.getId()));

                if (!goal.getUserId().equals(currentUserId)) {
                    return ResponseEntity.status(403).body("Unauthorized access to goal id: " + goal.getId());
                }

                if(updateGoal.getStatus() == GoalStatus.COMPLETED){
                    goal.setStatus(GoalStatus.COMPLETED);
                }
                goalsToUpdate.add(goal);
            }

            goalRepository.saveAll(goalsToUpdate);
            return ResponseEntity.ok("Goal update successfully");

        } catch(RuntimeException e) {
            throw e;
        } catch(Exception e) {
            throw new RuntimeException("Failed to update goal status", e);
        }
    }

    @Transactional
    public ResponseEntity<Object> updateMissionStatus(Long id, GoalReqDTO reqDTO){
        if (reqDTO.missions() == null || reqDTO.missions().isEmpty()) {
            return ResponseEntity.badRequest().body("Mission list cannot be empty");
        }

        List<Mission> missionsToUpdate = new ArrayList<>();

        try {
            for (Mission updateMission : reqDTO.missions()) {
                Mission mission = missionRepository.findById(updateMission.getId())
                        .orElseThrow(() -> new RuntimeException("Mission not found with id: " + updateMission.getId()));

                if (!mission.getGoal().getId().equals(id)) {
                    return ResponseEntity.badRequest().body("Goal id mismatch: " + mission.getId());
                }

                if(updateMission.getStatus() == MissionStatus.DONE){
                    mission.setStatus(MissionStatus.DONE);
                }
                missionsToUpdate.add(mission);
            }

            missionRepository.saveAll(missionsToUpdate);
            return ResponseEntity.ok("Mission update successfully");

        } catch(RuntimeException e) {
            throw e;
        } catch(Exception e) {
            throw new RuntimeException("Failed to update mission status", e);
        }
    }


}
