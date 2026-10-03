package com.lynq.analytics.service;

import com.lynq.analytics.cache.AnalyticsCaches;
import com.lynq.analytics.config.BenchmarkProperties;
import com.lynq.analytics.model.CandidateDailyBenchmarkEntity;
import com.lynq.analytics.repository.CandidateDailyBenchmarkRepository;
import com.lynq.analytics.stats.CandidateBenchmark;
import com.lynq.analytics.stats.CandidateBenchmark.BenchmarkPoint;
import com.lynq.analytics.stats.CandidateBenchmark.SkillUnlockCount;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateBenchmarkQueryService {

  private final CandidateDailyBenchmarkRepository candidateDailyBenchmarkRepository;
  private final BenchmarkProperties properties;

  public CandidateBenchmarkQueryService(
      CandidateDailyBenchmarkRepository candidateDailyBenchmarkRepository,
      BenchmarkProperties properties) {
    this.candidateDailyBenchmarkRepository = candidateDailyBenchmarkRepository;
    this.properties = properties;
  }

  @Cacheable(cacheNames = AnalyticsCaches.BENCHMARK, key = "#candidateId")
  @Transactional(readOnly = true)
  public CandidateBenchmark benchmark(String candidateId) {
    return candidateDailyBenchmarkRepository.findFirstByCandidateIdOrderBySnapshotOnDesc(candidateId)
        .map(latest -> benchmarkOf(latest, candidateDailyBenchmarkRepository
            .findByCandidateIdAndSnapshotOnGreaterThanEqualOrderBySnapshotOnAsc(candidateId,
                latest.getSnapshotOn().minusDays(properties.seriesDays() - 1L))))
        .orElseGet(() -> CandidateBenchmark.none(properties.reachThreshold()));
  }

  private static CandidateBenchmark benchmarkOf(CandidateDailyBenchmarkEntity latest,
      List<CandidateDailyBenchmarkEntity> series) {
    return new CandidateBenchmark(
        latest.getSnapshotOn(),
        latest.getMarketFit(),
        latest.getJobsScored(),
        latest.getAboveThresholdPct(),
        latest.getReachThreshold(),
        latest.getPeerPercentile(),
        latest.getPeerGroupSize(),
        latest.getPeerFitP25(),
        latest.getPeerFitMedian(),
        latest.getPeerFitP75(),
        latest.getSkillCoveragePct(),
        latest.getPeerCoverageMedian(),
        latest.getSkillUnlocks().stream()
            .map(unlock -> new SkillUnlockCount(unlock.getSkill(), unlock.getJobsUnlocked()))
            .toList(),
        series.stream()
            .map(point -> new BenchmarkPoint(point.getSnapshotOn(), point.getMarketFit(),
                point.getAboveThresholdPct(), point.getPeerPercentile(),
                point.getPeerGroupSize()))
            .toList());
  }
}
