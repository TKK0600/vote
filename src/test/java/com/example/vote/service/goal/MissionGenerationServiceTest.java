package com.example.vote.service.goal;

import com.example.vote.dto.goal.MissionResDTO;
import com.example.vote.modal.quest.*;
import com.example.vote.repository.goal.GoalConversationRepository;
import com.example.vote.repository.goal.MissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionGenerationServiceTest {

    @Mock private MissionRepository missionRepository;
    @Mock private GoalConversationRepository conversationRepository;

    @InjectMocks
    private MissionGenerationService service;

    private Goal goal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);

        goal = new Goal();
        goal.setId(10L);
        goal.setUserId(100L);
        goal.setTitle("Get fit");
        goal.setWeekRequired(4);
        goal.setCurrentWeek(2);
    }

    // ── Idempotency ──────────────────────────────────────────────

    @Test
    @DisplayName("week already exists → empty list, no generation")
    void idempotencySkips() {
        when(missionRepository.existsByGoalIdAndWeekNumber(10L, 2)).thenReturn(true);

        List<MissionResDTO> result = service.generateWeekMissions(goal, 2, LocalDate.now());

        assertTrue(result.isEmpty());
    }

    // ── buildUserMessage: week 1 ─────────────────────────────────

    @Test
    @DisplayName("buildUserMessage week 1 — includes conversation history")
    void buildUserMessageWeek1() throws Exception {
        GoalConversation c1 = new GoalConversation();
        c1.setRole("assistant"); c1.setContent("Q1"); c1.setSeqOrder(0);
        GoalConversation c2 = new GoalConversation();
        c2.setRole("user"); c2.setContent("A1"); c2.setSeqOrder(1);
        when(conversationRepository.findByGoalIdOrderBySeqOrderAsc(10L))
                .thenReturn(List.of(c1, c2));

        String msg = invokeBuildUserMessage(goal, 1);

        assertTrue(msg.contains("Get fit"));
        assertTrue(msg.contains("Week: 1 of 4"));
        assertTrue(msg.contains("User context from onboarding conversation:"));
        assertTrue(msg.contains("ASSISTANT: Q1"));
        assertTrue(msg.contains("USER: A1"));
        assertTrue(msg.contains("generate 7 daily missions for week 1"));
    }

    // ── buildUserMessage: week 2+ ────────────────────────────────

    @Test
    @DisplayName("buildUserMessage week 2+ — includes previous week results")
    void buildUserMessageWeek2Plus() throws Exception {
        Mission prev = new Mission();
        prev.setTargetDate(LocalDate.of(2026, 5, 18)); // a Monday
        prev.setStatus(MissionStatus.DONE);
        prev.setTitle("Workout");
        when(missionRepository.findByGoalIdAndWeekNumberOrderByTargetDate(10L, 1))
                .thenReturn(List.of(prev));

        String msg = invokeBuildUserMessage(goal, 2);

        assertTrue(msg.contains("Get fit"));
        assertTrue(msg.contains("Week: 2 of 4"));
        assertTrue(msg.contains("Last week results:"));
        assertTrue(msg.contains("MONDAY [DONE] Workout"));
        assertTrue(msg.contains("Completion rate last week: 100%"));
        assertTrue(msg.contains("Adjust difficulty accordingly"));
    }

    @Test
    @DisplayName("buildUserMessage — completion pct with mixed results")
    void completionPctMixed() throws Exception {
        Mission m1 = missionWithStatus(MissionStatus.DONE);
        Mission m2 = missionWithStatus(MissionStatus.SKIPPED);
        Mission m3 = missionWithStatus(MissionStatus.DONE);
        // weekNumber=3 → fetches week 2 data
        when(missionRepository.findByGoalIdAndWeekNumberOrderByTargetDate(10L, 2))
                .thenReturn(List.of(m1, m2, m3));

        String msg = invokeBuildUserMessage(goal, 3);

        assertTrue(msg.contains("Completion rate last week: 66%"));
    }

    @Test
    @DisplayName("buildUserMessage — empty previous week → 0%")
    void completionPctEmpty() throws Exception {
        when(missionRepository.findByGoalIdAndWeekNumberOrderByTargetDate(10L, 1))
                .thenReturn(List.of());

        String msg = invokeBuildUserMessage(goal, 2);

        assertTrue(msg.contains("Completion rate last week: 0%"));
    }

    // ── parseMissionsAndSave: valid input ────────────────────────

    @Test
    @DisplayName("parseMissionsAndSave — valid 7-mission JSON across all 7 days")
    void parseValidSevenMissions() throws Exception {
        String json = sevenDayJson(1, 1, 1, 1, 1, 1, 1); // one per day

        when(missionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<MissionResDTO> result = invokeParseMissionsAndSave(json, goal, 2,
                LocalDate.of(2026, 5, 25));

        assertEquals(7, result.size());
        // Check mapping
        MissionResDTO first = result.get(0);
        assertNotNull(first.title());
        assertEquals("EASY", first.difficulty());
        assertEquals(50, first.xpReward());
        assertEquals(2, first.weekNumber());
        assertEquals(LocalDate.of(2026, 5, 25), first.targetDate()); // day 1 = start
    }

    @Test
    @DisplayName("parseMissionsAndSave — 20 missions across 7 days (max valid)")
    void parseMaxMissions() throws Exception {
        String json = sevenDayJson(3, 3, 3, 3, 3, 3, 2); // 3+3+3+3+3+3+2 = 20

        when(missionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<MissionResDTO> result = invokeParseMissionsAndSave(json, goal, 3,
                LocalDate.of(2026, 6, 1));

        assertEquals(20, result.size());
    }

    @Test
    @DisplayName("parseMissionsAndSave — multiple missions on same day get consecutive target dates")
    void sameDayMultipleMissions() throws Exception {
        String json = """
            {
              "missions": [
                {"day":1,"title":"A","description":"a","difficulty":"EASY","xp":50},
                {"day":1,"title":"B","description":"b","difficulty":"EASY","xp":50},
                {"day":2,"title":"C","description":"c","difficulty":"MEDIUM","xp":120},
                {"day":3,"title":"D","description":"d","difficulty":"EASY","xp":50},
                {"day":4,"title":"E","description":"e","difficulty":"HARD","xp":250},
                {"day":5,"title":"F","description":"f","difficulty":"EASY","xp":50},
                {"day":6,"title":"G","description":"g","difficulty":"EASY","xp":50},
                {"day":7,"title":"H","description":"h","difficulty":"MEDIUM","xp":120}
              ]
            }""";

        when(missionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<MissionResDTO> result = invokeParseMissionsAndSave(json, goal, 1,
                LocalDate.of(2026, 5, 25));

        assertEquals(8, result.size());
        assertEquals(LocalDate.of(2026, 5, 25), result.get(0).targetDate()); // day 1
        assertEquals(LocalDate.of(2026, 5, 25), result.get(1).targetDate()); // day 1
        assertEquals(LocalDate.of(2026, 5, 26), result.get(2).targetDate()); // day 2
    }

    @Test
    @DisplayName("parseMissionsAndSave — cleaned JSON (markdown fences stripped)")
    void jsonWithMarkdownFences() throws Exception {
        String fencedJson = """
            ```json
            {"missions":[{"day":1,"title":"A","description":"d","difficulty":"EASY","xp":50},{"day":2,"title":"B","description":"d","difficulty":"EASY","xp":50},{"day":3,"title":"C","description":"d","difficulty":"MEDIUM","xp":120},{"day":4,"title":"D","description":"d","difficulty":"EASY","xp":50},{"day":5,"title":"E","description":"d","difficulty":"HARD","xp":250},{"day":6,"title":"F","description":"d","difficulty":"EASY","xp":50},{"day":7,"title":"G","description":"d","difficulty":"MEDIUM","xp":120}]}
            ```""";

        when(missionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<MissionResDTO> result = invokeParseMissionsAndSave(fencedJson, goal, 1,
                LocalDate.of(2026, 5, 25));

        assertEquals(7, result.size());
    }

    // ── parseMissionsAndSave: invalid input ──────────────────────

    @Test
    @DisplayName("null missions node → RuntimeException")
    void nullMissionsNode() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                invokeParseMissionsAndSave("{}", goal, 1, LocalDate.now()));
        assertTrue(ex.getMessage().contains("invalid missions format"));
    }

    @Test
    @DisplayName("too few missions (< 7) → RuntimeException")
    void tooFewMissions() {
        String json = """
            {"missions":[
              {"day":1,"title":"A","description":"d","difficulty":"EASY","xp":50},
              {"day":2,"title":"B","description":"d","difficulty":"EASY","xp":50}
            ]}""";

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                invokeParseMissionsAndSave(json, goal, 1, LocalDate.now()));
        assertTrue(ex.getMessage().contains("Expected between 7 and 20"));
        assertTrue(ex.getMessage().contains("AI returned 2"));
    }

    @Test
    @DisplayName("too many missions (> 20) → RuntimeException")
    void tooManyMissions() {
        StringBuilder sb = new StringBuilder("{\"missions\":[");
        for (int i = 0; i < 21; i++) {
            if (i > 0) sb.append(",");
            int day = (i % 7) + 1;
            sb.append("{\"day\":").append(day)
              .append(",\"title\":\"T").append(i)
              .append("\",\"description\":\"d\",\"difficulty\":\"EASY\",\"xp\":50}");
        }
        sb.append("]}");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                invokeParseMissionsAndSave(sb.toString(), goal, 1, LocalDate.now()));
        assertTrue(ex.getMessage().contains("Expected between 7 and 20"));
        assertTrue(ex.getMessage().contains("AI returned 21"));
    }

    @Test
    @DisplayName("day has > 5 missions → RuntimeException")
    void dayExceedsCap() {
        String json = """
            {"missions":[
              {"day":1,"title":"A","description":"d","difficulty":"EASY","xp":50},
              {"day":1,"title":"B","description":"d","difficulty":"EASY","xp":50},
              {"day":1,"title":"C","description":"d","difficulty":"EASY","xp":50},
              {"day":1,"title":"D","description":"d","difficulty":"EASY","xp":50},
              {"day":1,"title":"E","description":"d","difficulty":"EASY","xp":50},
              {"day":1,"title":"F","description":"d","difficulty":"EASY","xp":50},
              {"day":2,"title":"G","description":"d","difficulty":"EASY","xp":50}
            ]}""";

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                invokeParseMissionsAndSave(json, goal, 1, LocalDate.now()));
        assertTrue(ex.getMessage().contains("Day 1 has 6 missions"));
        assertTrue(ex.getMessage().contains("exceeds max of 5"));
    }

    @Test
    @DisplayName("not all 7 days covered → RuntimeException")
    void missingDays() {
        String json = """
            {"missions":[
              {"day":1,"title":"A","description":"d","difficulty":"EASY","xp":50},
              {"day":2,"title":"B","description":"d","difficulty":"EASY","xp":50},
              {"day":3,"title":"C","description":"d","difficulty":"EASY","xp":50},
              {"day":4,"title":"D","description":"d","difficulty":"EASY","xp":50},
              {"day":5,"title":"E","description":"d","difficulty":"EASY","xp":50},
              {"day":5,"title":"F","description":"d","difficulty":"EASY","xp":50},
              {"day":5,"title":"G","description":"d","difficulty":"EASY","xp":50}
            ]}""";

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                invokeParseMissionsAndSave(json, goal, 1, LocalDate.now()));
        assertTrue(ex.getMessage().contains("Not all 7 days"));
    }

    @Test
    @DisplayName("malformed JSON → RuntimeException")
    void malformedJson() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                invokeParseMissionsAndSave("not json at all", goal, 1, LocalDate.now()));
        assertTrue(ex.getMessage().contains("Failed to parse"));
    }

    // ── helpers ──────────────────────────────────────────────────

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

    private static Mission missionWithStatus(MissionStatus status) {
        Mission m = new Mission();
        m.setId(1L);
        m.setStatus(status);
        m.setTargetDate(LocalDate.now());
        m.setTitle("M");
        return m;
    }

    // ── Reflection helpers ───────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<MissionResDTO> invokeParseMissionsAndSave(
            String rawJson, Goal goal, int weekNumber, LocalDate weekStart) throws Exception {
        Method method = MissionGenerationService.class.getDeclaredMethod(
                "parseMissionsAndSave", String.class, Goal.class, int.class, LocalDate.class);
        method.setAccessible(true);
        try {
            return (List<MissionResDTO>) method.invoke(service, rawJson, goal, weekNumber, weekStart);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private String invokeBuildUserMessage(Goal goal, int weekNumber) throws Exception {
        Method method = MissionGenerationService.class.getDeclaredMethod(
                "buildUserMessage", Goal.class, int.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(service, goal, weekNumber);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }
}
