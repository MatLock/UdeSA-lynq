package com.lynq.analytics.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailySnapshotServiceTest {

  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T08:00:00Z"),
      ZoneId.of("America/Argentina/Buenos_Aires"));
  private static final LocalDate TODAY = LocalDate.parse("2026-10-03");

  @Mock
  private TagFrequencyService tagFrequencyService;

  @Mock
  private DailyAggregatesService dailyAggregatesService;

  @Mock
  private CandidateBenchmarkService candidateBenchmarkService;

  @Mock
  private ExecutorService executor;

  private DailySnapshotService dailySnapshotService;

  @BeforeEach
  void setUp() {
    dailySnapshotService = new DailySnapshotService(tagFrequencyService, dailyAggregatesService,
        candidateBenchmarkService, CLOCK, executor);
  }

  @Test
  void takesTheStepsInOrderForTodayInTheBackground() {
    runTheSubmittedRun();

    assertThat(dailySnapshotService.start(REQUEST_UUID), is(true));

    InOrder steps = inOrder(tagFrequencyService, dailyAggregatesService,
        candidateBenchmarkService);
    steps.verify(tagFrequencyService).recompute();
    steps.verify(dailyAggregatesService).snapshot(TODAY);
    steps.verify(candidateBenchmarkService).snapshot(TODAY);
    assertThat(dailySnapshotService.isRunning(), is(false));
  }

  @Test
  void logsTheRunUnderTheRequestUuidOfTheTrigger() {
    runTheSubmittedRun();
    doAnswer(invocation -> {
      assertThat(MDC.get("requestId"), is(REQUEST_UUID));
      return null;
    }).when(dailyAggregatesService).snapshot(TODAY);

    dailySnapshotService.start(REQUEST_UUID);

    verify(dailyAggregatesService).snapshot(TODAY);
    assertThat(MDC.get("requestId"), is(nullValue()));
  }

  @Test
  void goesOnWithTheNextStepWhenOneFails() {
    runTheSubmittedRun();
    doThrow(new IllegalStateException("redis is down")).when(dailyAggregatesService)
        .snapshot(TODAY);

    dailySnapshotService.start(REQUEST_UUID);

    verify(candidateBenchmarkService).snapshot(TODAY);
  }

  @Test
  void refusesASecondRunWhileOneIsRunning() {
    doNothing().when(executor).execute(any());

    assertThat(dailySnapshotService.start(REQUEST_UUID), is(true));
    assertThat(dailySnapshotService.start(REQUEST_UUID), is(false));

    assertThat(dailySnapshotService.isRunning(), is(true));
    verify(executor).execute(any());
  }

  @Test
  void releasesTheRunWhenTheExecutorRefusesIt() {
    doThrow(new IllegalStateException("shut down")).when(executor).execute(any());

    assertThrows(IllegalStateException.class, () -> dailySnapshotService.start(REQUEST_UUID));

    assertThat(dailySnapshotService.isRunning(), is(false));
  }

  @Test
  void recomputesTheTagFrequencyOnStartupWhenItIsEmpty() {
    when(tagFrequencyService.isEmpty()).thenReturn(true);

    dailySnapshotService.recomputeTagFrequencyIfEmpty();

    verify(tagFrequencyService).recompute();
  }

  @Test
  void leavesAFilledTagFrequencyForTheNextSnapshotOnStartup() {
    when(tagFrequencyService.isEmpty()).thenReturn(false);

    dailySnapshotService.recomputeTagFrequencyIfEmpty();

    verify(tagFrequencyService, never()).recompute();
  }

  @Test
  void stopsTheExecutorOnShutdown() {
    dailySnapshotService.shutdown();

    verify(executor).shutdownNow();
  }

  private void runTheSubmittedRun() {
    doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(executor).execute(any());
  }
}
