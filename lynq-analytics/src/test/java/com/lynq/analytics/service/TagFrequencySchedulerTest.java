package com.lynq.analytics.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagFrequencySchedulerTest {

  @Mock
  private TagFrequencyService tagFrequencyService;

  private TagFrequencyScheduler tagFrequencyScheduler;

  @BeforeEach
  void setUp() {
    tagFrequencyScheduler = new TagFrequencyScheduler(tagFrequencyService);
  }

  @Test
  void recomputesEveryDay() {
    tagFrequencyScheduler.recomputeDaily();

    verify(tagFrequencyService).recompute();
  }

  @Test
  void recomputesOnStartupWhenTheTableIsEmpty() {
    when(tagFrequencyService.isEmpty()).thenReturn(true);

    tagFrequencyScheduler.recomputeIfEmpty();

    verify(tagFrequencyService).recompute();
  }

  @Test
  void leavesAFilledTableForTheScheduleOnStartup() {
    when(tagFrequencyService.isEmpty()).thenReturn(false);

    tagFrequencyScheduler.recomputeIfEmpty();

    verify(tagFrequencyService, never()).recompute();
  }
}
