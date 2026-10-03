package com.lynq.analytics.stats;

import java.time.LocalDate;
import java.util.List;

public record CandidateBenchmark(
    LocalDate snapshotOn,
    Integer marketFit,
    int jobsScored,
    Integer aboveThresholdPct,
    int reachThreshold,
    Integer peerPercentile,
    int peerGroupSize,
    Integer peerFitP25,
    Integer peerFitMedian,
    Integer peerFitP75,
    Integer skillCoveragePct,
    Integer peerCoverageMedian,
    List<SkillUnlockCount> skillUnlocks,
    List<BenchmarkPoint> series) {

  public static CandidateBenchmark none(int reachThreshold) {
    return new CandidateBenchmark(null, null, 0, null, reachThreshold, null, 0, null, null, null,
        null, null, List.of(), List.of());
  }

  public record SkillUnlockCount(String skill, int jobsUnlocked) {
  }

  public record BenchmarkPoint(LocalDate snapshotOn, Integer marketFit, Integer aboveThresholdPct,
      Integer peerPercentile, int peerGroupSize) {
  }
}
