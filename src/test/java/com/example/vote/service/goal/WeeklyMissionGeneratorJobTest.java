package com.example.vote.service.goal;

import com.example.vote.dto.goal.MissionResDTO;
import com.example.vote.modal.quest.Goal;
import com.example.vote.modal.quest.GoalStatus;
import com.example.vote.repository.auth.UserAuthProviderRepository;
import com.example.vote.repository.goal.GoalRepository;
import com.example.vote.repository.goal.MissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyMissionGeneratorJobTest {

    @Mock private GoalRepository goalRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private MissionGenerationService missionGenerationService;
    @Mock private UserAuthProviderRepository userAuthProviderRepository;

    @InjectMocks private WeeklyMissionGeneratorJob job;

    private Goal activeGoal;

    @BeforeEach
    void setUp() {
        activeGoal = new Goal();
        activeGoal.setId(10L);
        activeGoal.setUserId(100L);
        activeGoal.setTitle("Get fit");
        activeGoal.setWeekRequired(4);
        activeGoal.setCurrentWeek(1);
        activeGoal.setCurrentWeekEndDate(LocalDateTime.now().minusHours(2)); // just expired
        activeGoal.setStatus(GoalStatus.ACTIVE);
    }

    // --- inactivity → FAILED ---

    @Test
    @DisplayName("user inactive 7+ days → goal marked FAILED")
    void inactivityMarksFailed() {
        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of(activeGoal));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(100L))
                .thenReturn(LocalDateTime.now().minusDays(10));

        job.run();

        assertEquals(GoalStatus.FAILED, activeGoal.getStatus());
        verify(goalRepository).save(activeGoal);
        verify(missionGenerationService, never()).generateWeekMissions(any(), anyInt(), any());
    }

    @Test
    @DisplayName("user with null lastLogin → FAILED")
    void nullLastLoginMarksFailed() {
        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of(activeGoal));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(100L)).thenReturn(null);

        job.run();

        assertEquals(GoalStatus.FAILED, activeGoal.getStatus());
        verify(goalRepository).save(activeGoal);
    }

    @Test
    @DisplayName("user logged in yesterday → NOT failed")
    void recentLoginProceeds() {
        activeGoal.setCurrentWeekEndDate(LocalDateTime.now().minusHours(2));
        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of(activeGoal));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(100L))
                .thenReturn(LocalDateTime.now().minusDays(1));
        when(missionRepository.existsByGoalIdAndWeekNumber(10L, 2)).thenReturn(false);
        when(missionGenerationService.generateWeekMissions(any(), eq(2), any()))
                .thenReturn(List.of());

        job.run();

        verify(missionGenerationService).generateWeekMissions(eq(activeGoal), eq(2), any());
    }

    // --- planned duration reached → COMPLETED ---

    @Test
    @DisplayName("nextWeek > weekRequired → COMPLETED (planned duration reached)")
    void plannedDurationComplete() {
        activeGoal.setCurrentWeek(4); // nextWeek = 5 > weekRequired = 4
        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of(activeGoal));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(100L))
                .thenReturn(LocalDateTime.now().minusHours(1));

        job.run();

        assertEquals(GoalStatus.COMPLETED, activeGoal.getStatus());
        verify(goalRepository).save(activeGoal);
        verify(missionGenerationService, never()).generateWeekMissions(any(), anyInt(), any());
    }

    @Test
    @DisplayName("nextWeek == weekRequired + 1 → COMPLETED")
    void exactBoundaryComplete() {
        activeGoal.setCurrentWeek(3);
        activeGoal.setWeekRequired(3); // nextWeek = 4 > 3
        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of(activeGoal));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(100L))
                .thenReturn(LocalDateTime.now().minusHours(1));

        job.run();

        assertEquals(GoalStatus.COMPLETED, activeGoal.getStatus());
    }

    // --- idempotency (week already exists) ---

    @Test
    @DisplayName("next week already generated → advance dates only, no re-generation")
    void weekAlreadyExists() {
        activeGoal.setCurrentWeekEndDate(LocalDateTime.now().minusHours(1));
        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of(activeGoal));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(100L))
                .thenReturn(LocalDateTime.now().minusHours(1));
        when(missionRepository.existsByGoalIdAndWeekNumber(10L, 2)).thenReturn(true);

        job.run();

        assertEquals(2, activeGoal.getCurrentWeek());
        verify(missionGenerationService, never()).generateWeekMissions(any(), anyInt(), any());
        verify(goalRepository).save(activeGoal);
    }

    // --- normal generation flow ---

    @Test
    @DisplayName("normal flow → generates missions, advances currentWeek and weekEndDate")
    void normalGeneration() {
        LocalDateTime originalEndDate = LocalDateTime.now().minusHours(1);
        activeGoal.setCurrentWeekEndDate(originalEndDate);
        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of(activeGoal));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(100L))
                .thenReturn(LocalDateTime.now().minusHours(1));
        when(missionRepository.existsByGoalIdAndWeekNumber(10L, 2)).thenReturn(false);
        when(missionGenerationService.generateWeekMissions(any(), eq(2), any()))
                .thenReturn(List.of());

        job.run();

        assertEquals(2, activeGoal.getCurrentWeek());
        assertEquals(originalEndDate.plusDays(7), activeGoal.getCurrentWeekEndDate());
        verify(goalRepository).save(activeGoal);
    }

    @Test
    @DisplayName("weekStart = currentWeekEndDate.toLocalDate()")
    void correctWeekStart() {
        LocalDateTime endDate = LocalDateTime.of(2026, 5, 25, 12, 0);
        activeGoal.setCurrentWeekEndDate(endDate);
        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of(activeGoal));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(100L))
                .thenReturn(LocalDateTime.now().minusHours(1));
        when(missionRepository.existsByGoalIdAndWeekNumber(10L, 2)).thenReturn(false);
        when(missionGenerationService.generateWeekMissions(any(), eq(2), any()))
                .thenReturn(List.of());

        job.run();

        verify(missionGenerationService).generateWeekMissions(
                eq(activeGoal), eq(2), eq(LocalDate.of(2026, 5, 25)));
    }

    // --- post-generation COMPLETED check ---

    @Test
    @DisplayName("after last week generation → COMPLETED when currentWeek >= weekRequired")
    void postGenerationCompleted() {
        activeGoal.setCurrentWeek(3);
        activeGoal.setWeekRequired(4); // nextWeek=4, after advance currentWeek=4 >= 4
        activeGoal.setCurrentWeekEndDate(LocalDateTime.now().minusHours(1));
        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of(activeGoal));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(100L))
                .thenReturn(LocalDateTime.now().minusHours(1));
        when(missionRepository.existsByGoalIdAndWeekNumber(10L, 4)).thenReturn(false);
        when(missionGenerationService.generateWeekMissions(any(), eq(4), any()))
                .thenReturn(List.of());

        job.run();

        assertEquals(4, activeGoal.getCurrentWeek());
        assertEquals(GoalStatus.COMPLETED, activeGoal.getStatus());
    }

    @Test
    @DisplayName("mid-plan generation → stays ACTIVE")
    void midPlanStaysActive() {
        activeGoal.setCurrentWeek(1);
        activeGoal.setWeekRequired(4); // nextWeek=2, after advance currentWeek=2 < 4
        activeGoal.setCurrentWeekEndDate(LocalDateTime.now().minusHours(1));
        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of(activeGoal));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(100L))
                .thenReturn(LocalDateTime.now().minusHours(1));
        when(missionRepository.existsByGoalIdAndWeekNumber(10L, 2)).thenReturn(false);
        when(missionGenerationService.generateWeekMissions(any(), eq(2), any()))
                .thenReturn(List.of());

        job.run();

        assertEquals(GoalStatus.ACTIVE, activeGoal.getStatus());
    }

    // --- exception isolation ---

    @Test
    @DisplayName("exception in one goal → other goals still processed")
    void exceptionIsolatesGoals() {
        Goal goodGoal = new Goal();
        goodGoal.setId(20L);
        goodGoal.setUserId(200L);
        goodGoal.setTitle("Learn guitar");
        goodGoal.setWeekRequired(4);
        goodGoal.setCurrentWeek(1);
        goodGoal.setCurrentWeekEndDate(LocalDateTime.now().minusHours(1));
        goodGoal.setStatus(GoalStatus.ACTIVE);

        // First goal throws when processing (null currentWeekEndDate)
        Goal badGoal = new Goal();
        badGoal.setId(99L);
        badGoal.setUserId(999L);
        badGoal.setTitle("Bad goal");
        badGoal.setStatus(GoalStatus.ACTIVE);
        badGoal.setCurrentWeek(1);
        badGoal.setWeekRequired(4);
        badGoal.setCurrentWeekEndDate(null); // will NPE on .toLocalDate()

        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of(badGoal, goodGoal));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(999L))
                .thenReturn(LocalDateTime.now().minusHours(1));
        when(userAuthProviderRepository.findMaxLastLoginByUserId(200L))
                .thenReturn(LocalDateTime.now().minusHours(1));
        when(missionRepository.existsByGoalIdAndWeekNumber(eq(20L), anyInt())).thenReturn(false);
        when(missionGenerationService.generateWeekMissions(eq(goodGoal), anyInt(), any()))
                .thenReturn(List.of());

        // badGoal will throw in processGoal, but goodGoal should still be processed
        assertDoesNotThrow(() -> job.run());

        // Verify goodGoal was processed: missions were generated
        verify(missionGenerationService).generateWeekMissions(eq(goodGoal), anyInt(), any());
    }

    // --- run() with no due goals ---

    @Test
    @DisplayName("no due goals → no processing")
    void noDueGoals() {
        when(goalRepository.findGoalsDueForWeeklyGeneration(any()))
                .thenReturn(List.of());

        job.run();

        verify(userAuthProviderRepository, never()).findMaxLastLoginByUserId(anyLong());
    }
}
