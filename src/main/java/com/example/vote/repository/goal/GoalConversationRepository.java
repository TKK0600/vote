package com.example.vote.repository.goal;

import com.example.vote.modal.quest.GoalConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalConversationRepository extends JpaRepository<GoalConversation, Long> {
    List<GoalConversation> findByGoalIdOrderBySeqOrderAsc(Long goalId);
    int countByGoalId(Long goalId);
}
