package com.example.vote.service.goal;

import com.example.vote.modal.quest.Goal;
import com.example.vote.modal.quest.GoalStatus;
import com.example.vote.repository.auth.UserAuthProviderRepository;
import com.example.vote.repository.goal.GoalRepository;
import com.example.vote.repository.goal.MissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyMissionGeneratorJob {

    private final GoalRepository goalRepository;
    private final MissionRepository missionRepository;
    private final MissionGenerationService missionGenerationService;
    private final UserAuthProviderRepository userAuthProviderRepository;

    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        List<Goal> dueGoals =
            goalRepository.findGoalsDueForWeeklyGeneration(LocalDateTime.now());

        log.info("Weekly generator: {} goal(s) due", dueGoals.size());

        for (Goal goal : dueGoals) {
            try {
                processGoal(goal);
            } catch (Exception e) {
                log.error("Failed to process goal {}: {}", goal.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    private void processGoal(Goal goal) {

        LocalDateTime lastLogin = userAuthProviderRepository
            .findMaxLastLoginByUserId(goal.getUserId());

        if (lastLogin == null || lastLogin.isBefore(LocalDateTime.now().minusDays(7))) {
            log.info("Goal {} owner inactive 7+ days — marking FAILED", goal.getId());
            goal.setStatus(GoalStatus.FAILED);
            goalRepository.save(goal);
            return;
        }

        int nextWeek = goal.getCurrentWeek() + 1;
        if (nextWeek > goal.getWeekRequired()) {
            log.info("Goal {} reached planned duration ({} weeks) — marking COMPLETED",
                goal.getId(), goal.getWeekRequired());
            goal.setStatus(GoalStatus.COMPLETED);
            goalRepository.save(goal);
            return;
        }

        if (missionRepository.existsByGoalIdAndWeekNumber(goal.getId(), nextWeek)) {
            log.warn("Week {} already exists for goal {} — advancing dates only",
                nextWeek, goal.getId());
            goal.setCurrentWeek(nextWeek);
            goal.setCurrentWeekEndDate(goal.getCurrentWeekEndDate().plusDays(7));
            goalRepository.save(goal);
            return;
        }

        LocalDate nextWeekStart = goal.getCurrentWeekEndDate().toLocalDate();
        missionGenerationService.generateWeekMissions(goal, nextWeek, nextWeekStart);

        goal.setCurrentWeek(nextWeek);
        goal.setCurrentWeekEndDate(goal.getCurrentWeekEndDate().plusDays(7));

        if (goal.getCurrentWeek() >= goal.getWeekRequired()) {
            goal.setStatus(GoalStatus.COMPLETED);
            log.info("Goal {} reached planned duration — marking COMPLETED", goal.getId());
        }
        goalRepository.save(goal);

        log.info("Goal {}: week {} generated successfully", goal.getId(), nextWeek);
    }
}
