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
    You are a goal coach helping personalise daily missions for a user.
    The user's goal is: "%s"

    You must ask EXACTLY %d short follow-up questions, one at a time.
    Cover: daily schedule and free time windows, current habits or baseline
    fitness/skill level, location and available resources, and any constraints
    (budget, injury, diet restrictions, work hours).

    Make every question specific to this goal and this person.
    Be warm, conversational, and brief.

    RULES:
    - Ask ONE question per response. Keep it under 2 sentences.
    - After receiving the answer to question %d, respond with ONLY this JSON
      and nothing else:
      {"done": true}
    - Never add explanation after that JSON.
    """;

    private static final String GENERATION_SYSTEM_PROMPT = """
    You are a daily mission planner. Based on the conversation history about
    the user's goal "%s", generate a personalised 5–10 day mission plan.

    CORE RULE — DAILY GRANULARITY:
    Every mission = exactly ONE concrete action the user completes in a single
    day. Never write a mission that spans multiple days or says "this week".
    If an activity naturally spans several days (e.g. "work out 6 hours total
    this week"), split it into individual daily missions (Day 1: 1 hr, Day 2:
    rest, Day 3: 1 hr, etc.).

    HOW TO DECIDE THE NUMBER OF MISSIONS (5–10):
    - Short, high-frequency goals (daily exercise, daily diet changes): 7 missions
      — one per day for a full week.
    - Goals with natural rest days or lower frequency: include rest/reflection
      days as lightweight missions (e.g. "Rest day: stretch for 10 minutes and
      log how you feel"). Total should still reach 5–7.
    - Complex goals with multiple distinct habit tracks: up to 10 missions to
      cover different dimensions across the week.

    MISSION QUALITY RULES:
    1. Be specific to the user's answers — reference their food, schedule,
       location, or constraints directly.
    2. Each mission must be completable in one sitting or one defined time
       window (e.g. "this morning", "after dinner", "at lunch").
    3. Title: short action phrase (≤8 words), starts with a verb.
    4. Description: 2–3 sentences. What to do, when to do it, and why it
       helps. No vague advice.
    5. Assign a "day_number" (1 = today, 2 = tomorrow, etc.) so the frontend
       can display them on a timeline.
    6. Difficulty and XP must reflect actual daily effort:
       - EASY  (+50 XP):  habit-level, ≤30 min, low willpower cost
       - MEDIUM (+120 XP): requires planning or moderate effort, 30–60 min
       - HARD  (+250 XP): high effort, discipline, or discomfort required, 60+ min

    BAD MISSION EXAMPLES (never generate these):
    ✗ "Complete 6 hours of workouts this week"   ← spans multiple days
    ✗ "Avoid fried food for 7 days"              ← no single-day action
    ✗ "Exercise 3 times this week"               ← vague, no day assigned
    ✗ "Work on your goal every day"              ← not specific

    GOOD MISSION EXAMPLES:
    ✓ Day 1 — "Walk 20 Minutes After Dinner Tonight" (EASY)
    ✓ Day 2 — "Swap Breakfast for Two Boiled Eggs" (EASY)
    ✓ Day 3 — "Complete a 45-Minute Bodyweight Session" (MEDIUM)
    ✓ Day 4 — "Rest Day: Stretch for 15 Minutes Before Bed" (EASY)
    ✓ Day 5 — "Cook One High-Protein Meal at Home" (MEDIUM)

    Return ONLY valid JSON — no markdown, no explanation:
    {
      "missions": [
        {
          "title": "...",
          "description": "...",
          "difficulty": "EASY",
          "xp": 50,
          "day_number": 1
        }
      ]
    }

    The array must have between 5 and 10 items.
    day_number must start at 1 and be sequential with no gaps (rest days
    still get a mission, just an EASY one).
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
