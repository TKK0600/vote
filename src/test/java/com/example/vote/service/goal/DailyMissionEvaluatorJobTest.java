package com.example.vote.service.goal;

import com.example.vote.modal.quest.Mission;
import com.example.vote.modal.quest.MissionStatus;
import com.example.vote.repository.goal.MissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyMissionEvaluatorJobTest {

    @Mock private MissionRepository missionRepository;

    @InjectMocks private DailyMissionEvaluatorJob job;

    @Test
    @DisplayName("ACTIVE missions with past targetDate → set to SKIPPED")
    void skipsOverdueActiveMissions() {
        Mission overdue = missionWithStatus(MissionStatus.ACTIVE, LocalDate.now().minusDays(2));
        when(missionRepository.findByStatusAndTargetDateBefore(
                eq(MissionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(overdue));

        job.run();

        assertEquals(MissionStatus.SKIPPED, overdue.getStatus());
    }

    @Test
    @DisplayName("multiple overdue missions → all SKIPPED, all passed to saveAll")
    void skipsMultipleOverdueMissions() {
        Mission m1 = missionWithStatus(MissionStatus.ACTIVE, LocalDate.now().minusDays(3));
        Mission m2 = missionWithStatus(MissionStatus.ACTIVE, LocalDate.now().minusDays(1));
        when(missionRepository.findByStatusAndTargetDateBefore(
                eq(MissionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(m1, m2));

        job.run();

        assertEquals(MissionStatus.SKIPPED, m1.getStatus());
        assertEquals(MissionStatus.SKIPPED, m2.getStatus());
        verify(missionRepository).saveAll(List.of(m1, m2));
    }

    @Test
    @DisplayName("today's targetDate → NOT skipped (only before today)")
    void doesNotSkipToday() {
        when(missionRepository.findByStatusAndTargetDateBefore(
                eq(MissionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of());

        job.run();

        verify(missionRepository).saveAll(List.of());
    }

    @Test
    @DisplayName("empty overdue → saveAll called with empty list")
    void emptyOverdueList() {
        when(missionRepository.findByStatusAndTargetDateBefore(
                eq(MissionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of());

        job.run();

        verify(missionRepository).saveAll(List.of());
    }

    @Test
    @DisplayName("already DONE / SKIPPED / FAILED missions never fetched by query")
    void onlyActiveMissionsAreFetched() {
        ArgumentCaptor<MissionStatus> statusCaptor = ArgumentCaptor.forClass(MissionStatus.class);
        when(missionRepository.findByStatusAndTargetDateBefore(
                statusCaptor.capture(), any(LocalDate.class)))
                .thenReturn(List.of());

        job.run();

        assertEquals(MissionStatus.ACTIVE, statusCaptor.getValue(),
                "Query must only target ACTIVE missions");
    }

    private static Mission missionWithStatus(MissionStatus status, LocalDate targetDate) {
        Mission m = new Mission();
        m.setId(1L);
        m.setStatus(status);
        m.setTargetDate(targetDate);
        m.setTitle("Test mission");
        m.setWeekNumber(1);
        return m;
    }
}
