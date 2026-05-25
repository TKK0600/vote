package com.example.vote.service.goal;

import com.example.vote.modal.quest.Mission;
import com.example.vote.modal.quest.MissionStatus;
import com.example.vote.repository.goal.MissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyMissionEvaluatorJob {

    private final MissionRepository missionRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void run() {
        List<Mission> overdue = missionRepository
            .findByStatusAndTargetDateBefore(MissionStatus.ACTIVE, LocalDate.now());

        log.info("Daily evaluator: auto-skipping {} overdue mission(s)", overdue.size());
        overdue.forEach(m -> m.setStatus(MissionStatus.SKIPPED));
        missionRepository.saveAll(overdue);
    }
}
