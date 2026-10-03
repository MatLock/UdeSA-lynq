package com.lynq.analytics.service;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class DailySnapshotService {

  private static final String MDC_REQUEST_ID = "requestId";

  private final TagFrequencyService tagFrequencyService;
  private final DailyAggregatesService dailyAggregatesService;
  private final CandidateBenchmarkService candidateBenchmarkService;
  private final Clock clock;
  private final ExecutorService executor;
  private final AtomicBoolean running = new AtomicBoolean(false);

  @Autowired
  public DailySnapshotService(TagFrequencyService tagFrequencyService,
      DailyAggregatesService dailyAggregatesService,
      CandidateBenchmarkService candidateBenchmarkService, Clock clock) {
    this(tagFrequencyService, dailyAggregatesService, candidateBenchmarkService, clock,
        Executors.newSingleThreadExecutor(Thread.ofPlatform().name("daily-snapshot").factory()));
  }

  DailySnapshotService(TagFrequencyService tagFrequencyService,
      DailyAggregatesService dailyAggregatesService,
      CandidateBenchmarkService candidateBenchmarkService, Clock clock,
      ExecutorService executor) {
    this.tagFrequencyService = tagFrequencyService;
    this.dailyAggregatesService = dailyAggregatesService;
    this.candidateBenchmarkService = candidateBenchmarkService;
    this.clock = clock;
    this.executor = executor;
  }

  public boolean start(String requestUuid) {
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    LocalDate snapshotOn = LocalDate.now(clock);
    try {
      executor.execute(() -> {
        try {
          run(snapshotOn, requestUuid);
        } finally {
          running.set(false);
        }
      });
    } catch (RuntimeException e) {
      running.set(false);
      throw e;
    }
    return true;
  }

  public boolean isRunning() {
    return running.get();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recomputeTagFrequencyIfEmpty() {
    if (tagFrequencyService.isEmpty()) {
      log.info("message= Tag frequency is empty, recomputing it on startup");
      tagFrequencyService.recompute();
    }
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
  }

  private void run(LocalDate snapshotOn, String requestUuid) {
    MDC.put(MDC_REQUEST_ID, requestUuid);
    try {
      log.info("message= Taking the daily snapshot, snapshotOn={}", snapshotOn);
      runStep("tag frequency", tagFrequencyService::recompute);
      runStep("daily aggregates", () -> dailyAggregatesService.snapshot(snapshotOn));
      runStep("candidate benchmark", () -> candidateBenchmarkService.snapshot(snapshotOn));
      log.info("message= Finished the daily snapshot, snapshotOn={}", snapshotOn);
    } finally {
      MDC.remove(MDC_REQUEST_ID);
    }
  }

  private static void runStep(String step, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      log.error("message= The daily snapshot step failed and wrote nothing, step={}", step, e);
    }
  }
}
