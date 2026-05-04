package com.example.vote.repository.goal;

import com.example.vote.modal.quest.Mission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionRepository extends JpaRepository<Mission, Long> {
    List<Mission> findByGoalId(Long goalId);
}