package com.example.vote.repository.goal;

import com.example.vote.modal.quest.Mission;
import com.example.vote.modal.quest.MissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MissionRepository extends JpaRepository<Mission, Long> {
    List<Mission> findByGoalId(Long goalId);

    @Query("SELECT m FROM Mission m LEFT JOIN Goal g ON m.goal.id = g.id WHERE g.userId = :userId")
    List<Mission> findByUserId(@Param("userId")Long userId);

    List<Mission> findByGoalIdOrderByTargetDate(Long goalId);
    
    List<Mission> findByGoalIdAndTargetDateAndStatus(Long goalId, LocalDate targetDate, MissionStatus status);

    List<Mission> findByStatusAndTargetDateBefore(MissionStatus status, LocalDate date);

    boolean existsByGoalIdAndWeekNumber(Long goalId, int weekNumber);

    List<Mission> findByGoalIdAndWeekNumberOrderByTargetDate(Long goalId, int weekNumber);
}