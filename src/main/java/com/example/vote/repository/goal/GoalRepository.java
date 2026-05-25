package com.example.vote.repository.goal;

import com.example.vote.modal.quest.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByUserId(@Param("userId") Long userId);

    @Query("SELECT g FROM Goal g WHERE g.userId = :userId AND g.status = 'ACTIVE'")
    List<Goal> findActiveByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT g FROM Goal g
        WHERE g.status = 'ACTIVE'
        AND g.currentWeekEndDate IS NOT NULL
        AND g.currentWeekEndDate <= :now
        """)
    List<Goal> findGoalsDueForWeeklyGeneration(@Param("now") LocalDateTime now);
}
