package com.example.vote.service.goal;

import com.example.vote.constant.CommonConst;
import com.example.vote.dto.goal.ChatResDTO;
import com.example.vote.dto.goal.GoalResDTO;
import com.example.vote.dto.goal.MissionResDTO;
import com.example.vote.exception.BusinessException;
import com.example.vote.modal.quest.Difficulty;
import com.example.vote.modal.quest.Goal;
import com.example.vote.modal.quest.GoalConversation;
import com.example.vote.modal.quest.GoalStatus;
import com.example.vote.modal.quest.Mission;
import com.example.vote.modal.quest.MissionStatus;
import com.example.vote.repository.goal.GoalConversationRepository;
import com.example.vote.repository.goal.GoalRepository;
import com.example.vote.repository.goal.MissionRepository;
import com.example.vote.util.PromptLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoalInterviewService {

    private final ChatClient chatClient;
    private final GoalRepository goalRepository;
    private final GoalConversationRepository conversationRepository;
    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper;

    private static final String INTERVIEW_SYSTEM_PROMPT = PromptLoader.load("prompt/goal_interview_prompt.md");

    private static final String GENERATION_SYSTEM_PROMPT = PromptLoader.load("prompt/goal_generation_prompt.md");

    // Called once when user submits their goal — gets the first question
    public ChatResDTO startInterview(Goal goal) {
        String systemPrompt = buildInterviewPrompt(goal.getTitle(), goal.getCategory());

        // No history yet — just ask for the first question
        String firstQuestion = chatClient.prompt()
                .system(systemPrompt)
                .user("My goal is: " + goal.getTitle() + ". Please ask your first question.")
                .call()
                .content();

        // Save the first assistant message
        saveConversationTurn(goal, "assistant", firstQuestion, 0);

        return new ChatResDTO(firstQuestion, false, 1, CommonConst.MAX_INTERVIEW_QUESTIONS);
    }

    // Called each time user submits an answer
    public ChatResDTO submitAnswer(Long goalId, String userAnswer) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new EntityNotFoundException("Goal not found"));

        List<GoalConversation> history = conversationRepository
                .findByGoalIdOrderBySeqOrderAsc(goalId);

        int nextSeq = history.size();

        // Save the user's answer
        saveConversationTurn(goal, "user", userAnswer, nextSeq);

        // Count total questions asked so far (assistant turns)
        long questionsAsked = history.stream()
                .filter(c -> "assistant".equals(c.getRole()))
                .count();

        // Hard ceiling — force done if max reached
        if (questionsAsked >= CommonConst.MAX_INTERVIEW_QUESTIONS) {
            return new ChatResDTO(null, true,
                    (int) questionsAsked, CommonConst.MAX_INTERVIEW_QUESTIONS);
        }

        // Build full history including the answer just saved
        List<GoalConversation> updatedHistory = conversationRepository
                .findByGoalIdOrderBySeqOrderAsc(goalId);

        List<Message> messages = buildMessageHistory(updatedHistory, null);
        String systemPrompt = buildInterviewPrompt(goal.getTitle(), goal.getCategory());

        String aiResponse = chatClient.prompt()
                .system(systemPrompt)
                .messages(messages)
                .call()
                .content();

        // Check if AI decided it has enough context
        String trimmed = aiResponse.trim();
        if (trimmed.startsWith("{") && trimmed.contains("\"interviewDone\"")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                if (node.has("interviewDone") && node.get("interviewDone").asBoolean()) {
                    return new ChatResDTO(null, true,
                            (int) questionsAsked, CommonConst.MAX_INTERVIEW_QUESTIONS);
                }
            } catch (Exception ignored) {
                // Not valid JSON — treat as a normal question
            }
        }

        // Normal question — save and return
        saveConversationTurn(goal, "assistant", aiResponse, nextSeq + 1);

        int currentQuestion = (int) questionsAsked + 1;
        return new ChatResDTO(aiResponse, false,
                currentQuestion, CommonConst.MAX_INTERVIEW_QUESTIONS);
    }

    // Called after interviewDone = true to generate missions
    @Transactional
    public List<MissionResDTO> generateMissions(Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new EntityNotFoundException("Goal not found"));

        List<GoalConversation> history = conversationRepository
                .findByGoalIdOrderBySeqOrderAsc(goalId);

        List<Message> messages = buildMessageHistory(history, null);
        String systemPrompt = String.format(GENERATION_SYSTEM_PROMPT, goal.getTitle());

        // Ask AI to generate missions based on full conversation
        String rawJson = chatClient.prompt()
                .system(systemPrompt)
                .messages(messages)
                .user("Now generate my personalised missions based on everything I told you.")
                .call()
                .content();

        // Parse JSON and save missions to DB
        return parseMissionsAndSave(rawJson, goal);
    }

    // --- Private helpers ---

    private String buildInterviewPrompt(String goalTitle, String category) {
        return String.format(INTERVIEW_SYSTEM_PROMPT,
                goalTitle,
                category,
                CommonConst.MAX_INTERVIEW_QUESTIONS,
                CommonConst.MAX_INTERVIEW_QUESTIONS);
    }

    private List<Message> buildMessageHistory(
            List<GoalConversation> history, String latestUserMessage) {

        List<Message> messages = new ArrayList<>();

        for (GoalConversation turn : history) {
            if ("user".equals(turn.getRole())) {
                messages.add(new UserMessage(turn.getContent()));
            } else {
                messages.add(new AssistantMessage(turn.getContent()));
            }
        }

        // Append the latest user message if provided
        if (latestUserMessage != null) {
            messages.add(new UserMessage(latestUserMessage));
        }

        return messages;
    }

    private void saveConversationTurn(Goal goal, String role,
                                      String content, int seqOrder) {
        GoalConversation turn = new GoalConversation();
        turn.setGoal(goal);
        turn.setRole(role);
        turn.setContent(content);
        turn.setSeqOrder(seqOrder);
        conversationRepository.save(turn);
    }

    private List<MissionResDTO> parseMissionsAndSave(String rawJson, Goal goal) {
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

            LocalDate weekStart = LocalDate.now();
            List<MissionResDTO> result = new ArrayList<>();

            for (JsonNode m : missionsNode) {
                int day = m.get("day").asInt();  // 1–7

                Mission mission = new Mission();
                mission.setGoal(goal);
                mission.setTitle(m.get("title").asText());
                mission.setDescription(m.get("description").asText());
                mission.setDifficulty(Difficulty.valueOf(m.get("difficulty").asText()));
                mission.setXpReward(m.get("xp").asInt());
                mission.setWeekNumber(goal.getCurrentWeek());           // always 1 on first generation
                mission.setTargetDate(weekStart.plusDays(day - 1));     // day 1 = today, day 7 = today+6
                mission.setStatus(MissionStatus.ACTIVE);

                missionRepository.save(mission);

                result.add(new MissionResDTO(
                    mission.getId(),
                    mission.getGoal().getId(),
                    mission.getTitle(),
                    mission.getDescription(),
                    mission.getDifficulty().name(),
                    mission.getXpReward(),
                    mission.getWeekNumber(),
                    mission.getTargetDate(),
                    mission.getStatus()
                ));
            }

            // Set week cycle expiry and activate goal
            goal.setCurrentWeekEndDate(LocalDateTime.now().plusDays(7));
            goal.setStatus(GoalStatus.ACTIVE);
            goalRepository.save(goal);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI mission response: " + e.getMessage(), e);
        }
    }

    public List<MissionResDTO> getMissions(Long goalId, Long userId) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new EntityNotFoundException("Goal not found"));
        if (!goal.getUserId().equals(userId))
            throw new BusinessException("Access denied");

        return missionRepository.findByGoalIdOrderByTargetDate(goalId)
            .stream()
            .map(m -> new MissionResDTO(
                m.getId(), m.getGoal().getId(), m.getTitle(), m.getDescription(),
                m.getDifficulty().name(), m.getXpReward(),
                m.getWeekNumber(), m.getTargetDate(), m.getStatus()))
            .toList();
    }

    public List<MissionResDTO> getTodaysMissions(Long goalId, Long userId) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new EntityNotFoundException("Goal not found"));
        if (!goal.getUserId().equals(userId))
            throw new BusinessException("Access denied");

        return missionRepository.findByGoalIdAndTargetDateAndStatus(goalId, LocalDate.now(), MissionStatus.ACTIVE)
            .stream()
            .map(this::toMissionResDTO)
            .toList();
    }

    @Transactional
    public MissionResDTO completeMission(Long missionId, Long userId) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new EntityNotFoundException("Mission not found"));

        if (!mission.getGoal().getUserId().equals(userId))
            throw new BusinessException("Access denied");

        if (mission.getStatus() != MissionStatus.ACTIVE)
            throw new BusinessException("Mission is not active");

        mission.setStatus(MissionStatus.DONE);
        missionRepository.save(mission);
        return toMissionResDTO(mission);
    }

    @Transactional
    public MissionResDTO skipMission(Long missionId, Long userId) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new EntityNotFoundException("Mission not found"));

        if (!mission.getGoal().getUserId().equals(userId))
            throw new BusinessException("Access denied");

        if (mission.getStatus() != MissionStatus.ACTIVE)
            throw new BusinessException("Mission is not active");

        mission.setStatus(MissionStatus.SKIPPED);
        missionRepository.save(mission);
        return toMissionResDTO(mission);
    }

    @Transactional
    public GoalResDTO completeGoal(Long goalId, Long userId) {
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new EntityNotFoundException("Goal not found"));

        if (!goal.getUserId().equals(userId))
            throw new BusinessException("Access denied");

        if (goal.getStatus() != GoalStatus.ACTIVE)
            throw new BusinessException("Only active goals can be marked complete");

        goal.setStatus(GoalStatus.COMPLETED);
        goalRepository.save(goal);
        return toGoalResDTO(goal);
    }

    // --- Private helpers ---

    private MissionResDTO toMissionResDTO(Mission m) {
        return new MissionResDTO(
            m.getId(), m.getGoal().getId(), m.getTitle(), m.getDescription(),
            m.getDifficulty().name(), m.getXpReward(),
            m.getWeekNumber(), m.getTargetDate(), m.getStatus());
    }

    private GoalResDTO toGoalResDTO(Goal g) {
        return new GoalResDTO(
            g.getId(), g.getTitle(), g.getStatus().name(), g.getCategory(), g.getCreatedAt());
    }
}
