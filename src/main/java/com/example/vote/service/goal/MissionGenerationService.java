package com.example.vote.service.goal;

import com.example.vote.constant.CommonConst;
import com.example.vote.dto.goal.MissionResDTO;
import com.example.vote.mapstruct.goal.GoalMapStruct;
import com.example.vote.modal.quest.Difficulty;
import com.example.vote.modal.quest.Goal;
import com.example.vote.modal.quest.GoalConversation;
import com.example.vote.modal.quest.Mission;
import com.example.vote.modal.quest.MissionStatus;
import com.example.vote.repository.goal.GoalConversationRepository;
import com.example.vote.repository.goal.MissionRepository;
import com.example.vote.util.PromptLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionGenerationService {

    private final ChatClient chatClient;
    private final MissionRepository missionRepository;
    private final GoalConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;
    private final GoalMapStruct goalMapStruct;

    private static final String GENERATION_SYSTEM_PROMPT =
        PromptLoader.load("prompt/goal_generation_prompt.md");

    @Transactional
    public List<MissionResDTO> generateWeekMissions(Goal goal, int weekNumber, LocalDate weekStart) {

        if (missionRepository.existsByGoalIdAndWeekNumber(goal.getId(), weekNumber)) {
            log.warn("Week {} missions already exist for goal {} — skipping generation",
                weekNumber, goal.getId());
            return List.of();
        }

        String userMessage = buildUserMessage(goal, weekNumber);
        String systemPrompt = String.format(GENERATION_SYSTEM_PROMPT, goal.getTitle());

        String rawJson = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        return parseMissionsAndSave(rawJson, goal, weekNumber, weekStart);
    }

    private String buildUserMessage(Goal goal, int weekNumber) {
        if (weekNumber == 1) {
            List<GoalConversation> history = conversationRepository
                .findByGoalIdOrderBySeqOrderAsc(goal.getId());

            StringBuilder sb = new StringBuilder();
            sb.append("Goal: ").append(goal.getTitle()).append("\n");
            sb.append("Week: 1 of ").append(goal.getWeekRequired()).append("\n\n");
            sb.append("User context from onboarding conversation:\n");
            history.forEach(c ->
                sb.append(c.getRole().toUpperCase()).append(": ").append(c.getContent()).append("\n")
            );
            sb.append("\nNow generate 7 daily missions for week 1.");
            return sb.toString();

        } else {
            List<Mission> previousMissions = missionRepository
                .findByGoalIdAndWeekNumberOrderByTargetDate(goal.getId(), weekNumber - 1);

            StringBuilder sb = new StringBuilder();
            sb.append("Goal: ").append(goal.getTitle()).append("\n");
            sb.append("Week: ").append(weekNumber).append(" of ").append(goal.getWeekRequired()).append("\n\n");
            sb.append("Last week results:\n");
            previousMissions.forEach(m ->
                sb.append("Day ").append(m.getTargetDate().getDayOfWeek())
                  .append(" [").append(m.getStatus()).append("] ")
                  .append(m.getTitle()).append("\n")
            );

            long doneCount = previousMissions.stream()
                .filter(m -> m.getStatus() == MissionStatus.DONE).count();
            int completionPct = (int) ((doneCount * 100) / Math.max(previousMissions.size(), 1));
            sb.append("\nCompletion rate last week: ").append(completionPct).append("%\n");
            sb.append("Adjust difficulty accordingly. Now generate 7 daily missions for week ")
              .append(weekNumber).append(".");
            return sb.toString();
        }
    }

    private List<MissionResDTO> parseMissionsAndSave(
            String rawJson, Goal goal, int weekNumber, LocalDate weekStart) {
        try {
            String cleaned = rawJson.replaceAll("```json|```", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode missionsNode = root.get("missions");

            if (missionsNode == null || !missionsNode.isArray()) {
                throw new RuntimeException("AI returned invalid missions format. Raw: " + rawJson);
            }

            int total = missionsNode.size();
            if (total < CommonConst.MIN_MISSIONS_PER_WEEK || total > CommonConst.MAX_MISSIONS_PER_WEEK) {
                throw new RuntimeException(
                    "AI returned " + total + " missions. Expected between "
                    + CommonConst.MIN_MISSIONS_PER_WEEK + " and "
                    + CommonConst.MAX_MISSIONS_PER_WEEK + ". Raw: " + rawJson);
            }

            // Validate per-day cap
            Map<Integer, Long> perDay = new HashMap<>();
            for (JsonNode m : missionsNode) {
                int day = m.get("day").asInt();
                perDay.merge(day, 1L, Long::sum);
            }
            for (Map.Entry<Integer, Long> entry : perDay.entrySet()) {
                if (entry.getValue() > CommonConst.MAX_MISSIONS_PER_DAY) {
                    throw new RuntimeException(
                        "Day " + entry.getKey() + " has " + entry.getValue()
                        + " missions, exceeds max of " + CommonConst.MAX_MISSIONS_PER_DAY);
                }
            }

            // Validate all 7 days are covered
            if (perDay.size() < 7) {
                throw new RuntimeException(
                    "Not all 7 days have at least one mission. Days present: " + perDay.keySet());
            }

            List<MissionResDTO> result = new ArrayList<>();
            for (JsonNode m : missionsNode) {
                int day = m.get("day").asInt();

                Mission mission = new Mission();
                mission.setGoal(goal);
                mission.setTitle(m.get("title").asText());
                mission.setDescription(m.get("description").asText());
                mission.setDifficulty(Difficulty.valueOf(m.get("difficulty").asText()));
                mission.setXpReward(m.get("xp").asInt());
                mission.setWeekNumber(weekNumber);
                mission.setTargetDate(weekStart.plusDays(day - 1));
                mission.setStatus(MissionStatus.ACTIVE);

                missionRepository.save(mission);

                result.add(goalMapStruct.toMissionResDTO(mission));
            }
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse mission response: " + e.getMessage(), e);
        }
    }
}
