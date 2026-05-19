package com.example.vote.rest;

import com.example.vote.dto.goal.ChatReqDTO;
import com.example.vote.dto.goal.ChatResDTO;
import com.example.vote.dto.goal.CreateGoalReqDTO;
import com.example.vote.dto.goal.GoalReqDTO;
import com.example.vote.dto.goal.MissionResDTO;
import com.example.vote.modal.quest.Goal;
import com.example.vote.repository.goal.GoalRepository;
import com.example.vote.repository.goal.MissionRepository;
import com.example.vote.service.goal.GoalInterviewService;
import com.example.vote.service.goal.GoalService;
import com.example.vote.util.RequestUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalResource {

    private final GoalRepository goalRepository;
    private final GoalInterviewService interviewService;
    private final GoalService goalService;
    private final MissionRepository missionRepository;

    // Step 1: Create goal → get first question back immediately
    @PostMapping
    public ResponseEntity<Map<String, Object>> createGoal(
            @RequestBody @Valid CreateGoalReqDTO request) {

        Goal goal = new Goal();
        goal.setUserId(RequestUserUtil.getCurrentUserId());
        goal.setTitle(request.title());
        goal.setCategory(request.category());
        goal.setEndDate(LocalDate.now().plusDays(request.duration()).atStartOfDay());
        goalRepository.save(goal);

        // Kick off the interview immediately
        ChatResDTO firstQuestion = interviewService.startInterview(goal);

        return ResponseEntity.ok(Map.of(
                "goalId", goal.getId(),
                "chat", firstQuestion
        ));
    }

    // Step 2: User submits an answer, get next question (or interviewDone=true)
    @PostMapping("/{id}/chat")
    public ResponseEntity<ChatResDTO> chat(
            @PathVariable Long id,
            @RequestBody @Valid ChatReqDTO request) {

        ChatResDTO response = interviewService.submitAnswer(id, request.answer());
        return ResponseEntity.ok(response);
    }

    // Step 3: Called when frontend sees interviewDone=true
    @PostMapping("/{id}/missions/generate")
    public ResponseEntity<List<MissionResDTO>> generateMissions(
            @PathVariable Long id) {

        List<MissionResDTO> missions = interviewService.generateMissions(id);
        return ResponseEntity.ok(missions);
    }

    // Get all missions for a goal
    @GetMapping("/{id}/missions")
    public ResponseEntity<List<MissionResDTO>> getMissions(@PathVariable Long id) {

        List<MissionResDTO> missions = missionRepository
                .findByGoalId(id)
                .stream()
                .map(m -> new MissionResDTO(m.getId(), m.getGoal().getId(), m.getTitle(),
                        m.getDescription(), m.getDifficulty().name(), m.getXpReward()))
                .toList();

        return ResponseEntity.ok(missions);
    }

    @GetMapping("/mission/list")
    public ResponseEntity<List<MissionResDTO>> getMissionList() {
        Long currentUserId = RequestUserUtil.getCurrentUserId();
        List<MissionResDTO> missions = missionRepository
                .findByUserId(currentUserId)
                .stream()
                .map(m -> new MissionResDTO(m.getId(), m.getGoal().getId(), m.getTitle(),
                        m.getDescription(), m.getDifficulty().name(), m.getXpReward()))
                .toList();

        return ResponseEntity.ok(missions);
    }

    @GetMapping("list")
    public ResponseEntity<List<Map<String, Object>>> listGoals() {
        List<Map<String, Object>> goals = goalRepository.findActiveByUserId(RequestUserUtil.getCurrentUserId())
                .stream()
                .map(g -> {
                    Map<String, Object> goalMap = new HashMap<>();
                    goalMap.put("id", g.getId());
                    goalMap.put("title", g.getTitle());
                    goalMap.put("category", g.getCategory());
                    goalMap.put("status", g.getStatus());
                    goalMap.put("createdAt", g.getCreatedAt());
                    return goalMap;
                })
                .toList();
        return ResponseEntity.ok(goals);
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateGoal(@RequestBody GoalReqDTO reqDTO) {
        return goalService.updateGoalStatus(reqDTO);
    }

    @PutMapping("/{id}/mission/update")
    public ResponseEntity<?> updateMission(@PathVariable Long id, @RequestBody GoalReqDTO reqDTO) {
        return goalService.updateMissionStatus(id, reqDTO);
    }
}
