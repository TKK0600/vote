package com.example.vote.service.goal;

import com.example.vote.dto.goal.GoalResDTO;
import com.example.vote.dto.goal.MissionGenerationResDTO;
import com.example.vote.dto.goal.MissionResDTO;
import com.example.vote.dto.goal.UpdateGoalReqDTO;
import com.example.vote.exception.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import com.example.vote.modal.quest.Goal;
import com.example.vote.modal.quest.GoalConversation;
import com.example.vote.modal.quest.GoalStatus;
import com.example.vote.repository.goal.GoalConversationRepository;
import com.example.vote.repository.goal.GoalRepository;
import com.example.vote.repository.goal.MissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalInterviewServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private GoalRepository goalRepository;
    @Mock private GoalConversationRepository conversationRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec responseSpec;

    @InjectMocks
    private GoalInterviewService service;

    private Goal goal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);

        goal = new Goal();
        goal.setId(10L);
        goal.setUserId(100L);
        goal.setTitle("Run a marathon");
        goal.setWeekRequired(52);
        goal.setCurrentWeek(1);
    }

    // ── evaluateFeasibility: achievable true ──────────────────────

    @Test
    @DisplayName("evaluateFeasibility returns achievable=true")
    void feasibilityTrue() throws Exception {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("{\"achievable\":true}");

        GoalConversation c1 = new GoalConversation();
        c1.setRole("assistant"); c1.setContent("What's your running experience?"); c1.setSeqOrder(0);
        GoalConversation c2 = new GoalConversation();
        c2.setRole("user"); c2.setContent("I ran a half marathon last year."); c2.setSeqOrder(1);

        Object result = invokeEvaluateFeasibility(goal, List.of(c1, c2));

        assertNotNull(result);
        boolean achievable = (boolean) result.getClass().getMethod("achievable").invoke(result);
        assertTrue(achievable);
    }

    // ── evaluateFeasibility: achievable false with note ───────────

    @Test
    @DisplayName("evaluateFeasibility returns achievable=false with note")
    void feasibilityFalseWithNote() throws Exception {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(
                "{\"achievable\":false,\"note\":\"You need at least 12 months of training for a marathon.\"}");

        GoalConversation c1 = new GoalConversation();
        c1.setRole("user"); c1.setContent("I have never run before."); c1.setSeqOrder(0);

        Object result = invokeEvaluateFeasibility(goal, List.of(c1));

        boolean achievable = (boolean) result.getClass().getMethod("achievable").invoke(result);
        String note = (String) result.getClass().getMethod("note").invoke(result);
        assertFalse(achievable);
        assertTrue(note.contains("12 months"));
    }

    // ── evaluateFeasibility: malformed JSON defaults to true ──────

    @Test
    @DisplayName("evaluateFeasibility defaults to achievable=true on malformed JSON")
    void feasibilityMalformedJsonDefaultsTrue() throws Exception {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("not valid json at all");

        GoalConversation c1 = new GoalConversation();
        c1.setRole("user"); c1.setContent("I want to get fit."); c1.setSeqOrder(0);

        Object result = invokeEvaluateFeasibility(goal, List.of(c1));

        boolean achievable = (boolean) result.getClass().getMethod("achievable").invoke(result);
        String note = (String) result.getClass().getMethod("note").invoke(result);
        assertTrue(achievable);
        assertNull(note);
    }

    // ── generateMissions: achievable → returns wrapper with missions ──

    @Test
    @DisplayName("generateMissions with achievable=true returns wrapper with missions")
    void generateMissionsAchievable() {
        goal.setWeekRequired(12);

        GoalConversation c1 = new GoalConversation();
        c1.setRole("user"); c1.setContent("I run 4 times a week."); c1.setSeqOrder(0);
        when(conversationRepository.findByGoalIdOrderBySeqOrderAsc(10L))
                .thenReturn(List.of(c1));

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

        // Feasibility AI call returns achievable
        String feasibilityJson = "{\"achievable\":true}";

        // Generation AI call returns valid missions JSON
        String generationJson = sevenDayJson(1, 1, 1, 1, 1, 1, 1);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content())
                .thenReturn(feasibilityJson)   // first call: feasibility
                .thenReturn(generationJson);    // second call: generation

        when(missionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MissionGenerationResDTO result = service.generateMissions(10L);

        assertTrue(result.achievable());
        assertNull(result.note());
        assertNotNull(result.missions());
        assertEquals(7, result.missions().size());
    }

    // ── generateMissions: not achievable → returns wrapper with null missions ──

    @Test
    @DisplayName("generateMissions with achievable=false returns note and null missions")
    void generateMissionsNotAchievable() {
        goal.setWeekRequired(2);

        GoalConversation c1 = new GoalConversation();
        c1.setRole("user"); c1.setContent("I have never exercised."); c1.setSeqOrder(0);
        when(conversationRepository.findByGoalIdOrderBySeqOrderAsc(10L))
                .thenReturn(List.of(c1));

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
                when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content())
                .thenReturn("{\"achievable\":false,\"note\":\"Marathon training takes months, not weeks.\"}");

        MissionGenerationResDTO result = service.generateMissions(10L);

        assertFalse(result.achievable());
        assertTrue(result.note().contains("Marathon training"));
        assertNull(result.missions());

        // Goal should NOT be activated
        assertEquals(GoalStatus.DRAFT, goal.getStatus());
    }

    // ── Reflection helper ─────────────────────────────────────────

    private Object invokeEvaluateFeasibility(Goal goal, List<GoalConversation> history) throws Exception {
        Method method = GoalInterviewService.class.getDeclaredMethod(
                "evaluateFeasibility", Goal.class, List.class);
        method.setAccessible(true);
        try {
            return method.invoke(service, goal, history);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private String sevenDayJson(int... countsPerDay) {
        StringBuilder sb = new StringBuilder("{\"missions\":[");
        boolean first = true;
        for (int day = 0; day < 7; day++) {
            int count = countsPerDay[day];
            for (int i = 0; i < count; i++) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"day\":").append(day + 1)
                  .append(",\"title\":\"Day ").append(day + 1).append(" #").append(i + 1)
                  .append("\",\"description\":\"desc\",\"difficulty\":\"EASY\",\"xp\":50}");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    // ── updateGoal tests ────────────────────────────────────────

    @Test
    @DisplayName("updateGoal successfully updates weekRequired")
    void updateGoalSuccess() {
        goal.setWeekRequired(4);
        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

        UpdateGoalReqDTO request = new UpdateGoalReqDTO(10L, 8);
        GoalResDTO result = service.updateGoal(request, 100L);

        assertNotNull(result);
        assertEquals(8, goal.getWeekRequired());
        assertEquals("Run a marathon", result.getTitle());
    }

    @Test
    @DisplayName("updateGoal throws for non-existent goal")
    void updateGoalNotFound() {
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateGoalReqDTO request = new UpdateGoalReqDTO(99L, 8);

        assertThrows(EntityNotFoundException.class,
                () -> service.updateGoal(request, 100L));
    }

    @Test
    @DisplayName("updateGoal throws for non-owner")
    void updateGoalAccessDenied() {
        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

        UpdateGoalReqDTO request = new UpdateGoalReqDTO(10L, 8);

        assertThrows(BusinessException.class,
                () -> service.updateGoal(request, 999L));
    }

    @Test
    @DisplayName("updateGoal with null weekRequired leaves field unchanged")
    void updateGoalNullWeekRequired() {
        goal.setWeekRequired(12);
        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));

        UpdateGoalReqDTO request = new UpdateGoalReqDTO(10L, null);
        GoalResDTO result = service.updateGoal(request, 100L);

        assertEquals(12, goal.getWeekRequired());
        assertNotNull(result);
    }
}
