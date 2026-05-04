package com.example.vote.service.goal;

import com.example.vote.dto.goal.ChatResDTO;
import com.example.vote.dto.goal.MissionResDTO;
import com.example.vote.modal.quest.Difficulty;
import com.example.vote.modal.quest.Goal;
import com.example.vote.modal.quest.GoalConversation;
import com.example.vote.modal.quest.GoalStatus;
import com.example.vote.modal.quest.Mission;
import com.example.vote.repository.goal.GoalConversationRepository;
import com.example.vote.repository.goal.GoalRepository;
import com.example.vote.repository.goal.MissionRepository;
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

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalInterviewService {

    private final ChatClient chatClient;
    private final GoalRepository goalRepository;
    private final GoalConversationRepository conversationRepository;
    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_QUESTIONS = 4;

    private static final String INTERVIEW_SYSTEM_PROMPT = """
        You are a goal coach helping personalise missions for a user.
        The user's goal is: "%s"

        You must ask EXACTLY %d short follow-up questions, one at a time.
        Cover: available time, current habits/lifestyle, location or culture, constraints.
        Make questions specific to this goal. Be warm and conversational.

        RULES:
        - Ask ONE question per response. Keep it under 2 sentences.
        - After receiving the answer to question %d, respond with ONLY this JSON and nothing else:
          {"done": true}
        - Never add explanation after that JSON.
        """;

    private static final String GENERATION_SYSTEM_PROMPT = """
        You are a mission generator. Based on the conversation history about the user's
        goal "%s", generate 6 personalised missions.

        Return ONLY valid JSON — no markdown, no explanation:
        {
          "missions": [
            {"title":"...","description":"...","difficulty":"EASY","xp":50},
            {"title":"...","description":"...","difficulty":"EASY","xp":50},
            {"title":"...","description":"...","difficulty":"MEDIUM","xp":120},
            {"title":"...","description":"...","difficulty":"MEDIUM","xp":120},
            {"title":"...","description":"...","difficulty":"HARD","xp":250},
            {"title":"...","description":"...","difficulty":"HARD","xp":250}
          ]
        }

        Missions must reference the user's actual answers — make them specific to this person.
        """;

    // Called once when user submits their goal — gets the first question
    public ChatResDTO startInterview(Goal goal) {
        String systemPrompt = buildInterviewPrompt(goal.getTitle());

        // No history yet — just ask for the first question
        String firstQuestion = chatClient.prompt()
                .system(systemPrompt)
                .user("My goal is: " + goal.getTitle() + ". Please ask your first question.")
                .call()
                .content();

        // Save the first assistant message
        saveConversationTurn(goal, "assistant", firstQuestion, 0);

        return new ChatResDTO(firstQuestion, false, 1, MAX_QUESTIONS);
    }

    // Called each time user submits an answer
    public ChatResDTO submitAnswer(Long goalId, String userAnswer) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new EntityNotFoundException("Goal not found"));

        List<GoalConversation> history = conversationRepository
                .findByGoalIdOrderBySeqOrderAsc(goalId);

        int nextSeq = history.size();

        // Save the user's answer first
        saveConversationTurn(goal, "user", userAnswer, nextSeq);

        // Count how many user turns exist (= how many questions answered so far)
        long answeredCount = history.stream()
                .filter(c -> "user".equals(c.getRole()))
                .count() + 1; // +1 for the answer we just saved

        // If all questions answered, signal done — don't ask another question
        if (answeredCount >= MAX_QUESTIONS) {
            return new ChatResDTO(null, true,
                    (int) answeredCount, MAX_QUESTIONS);
        }

        // Build message list for Spring AI from DB history
        List<Message> messages = buildMessageHistory(history, userAnswer);
        String systemPrompt = buildInterviewPrompt(goal.getTitle());

        String nextQuestion = chatClient.prompt()
                .system(systemPrompt)
                .messages(messages)
                .call()
                .content();

        // Save AI's next question
        saveConversationTurn(goal, "assistant", nextQuestion, nextSeq + 1);

        int questionNumber = (int) answeredCount + 1;
        return new ChatResDTO(nextQuestion, false, questionNumber, MAX_QUESTIONS);
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

    private String buildInterviewPrompt(String goalTitle) {
        return String.format(INTERVIEW_SYSTEM_PROMPT,
                goalTitle, MAX_QUESTIONS, MAX_QUESTIONS);
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

            List<MissionResDTO> result = new ArrayList<>();
            for (JsonNode m : missionsNode) {
                Mission mission = new Mission();
                mission.setGoal(goal);
                mission.setTitle(m.get("title").asText());
                mission.setDescription(m.get("description").asText());
                mission.setDifficulty(
                        Difficulty.valueOf(m.get("difficulty").asText()));
                mission.setXpReward(m.get("xp").asInt());
                missionRepository.save(mission);

                result.add(new MissionResDTO(
                        mission.getId(), mission.getTitle(),
                        mission.getDescription(),
                        mission.getDifficulty().name(),
                        mission.getXpReward()
                ));
            }

            // Update goal status to ACTIVE now that missions exist
            goal.setStatus(GoalStatus.ACTIVE);
            goalRepository.save(goal);

            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI mission response", e);
        }
    }
}
