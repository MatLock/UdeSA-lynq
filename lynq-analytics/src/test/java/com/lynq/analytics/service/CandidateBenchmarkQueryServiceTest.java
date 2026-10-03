package com.lynq.analytics.service;

import com.lynq.analytics.config.BenchmarkProperties;
import com.lynq.analytics.model.CandidateDailyBenchmarkEntity;
import com.lynq.analytics.model.SkillUnlock;
import com.lynq.analytics.repository.CandidateDailyBenchmarkRepository;
import com.lynq.analytics.stats.CandidateBenchmark;
import com.lynq.analytics.stats.CandidateBenchmark.BenchmarkPoint;
import com.lynq.analytics.stats.CandidateBenchmark.SkillUnlockCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateBenchmarkQueryServiceTest {

  private static final String CANDIDATE_ID = "11111111-1111-1111-1111-111111111111";
  private static final LocalDate LATEST = LocalDate.parse("2026-10-03");

  @Mock
  private CandidateDailyBenchmarkRepository candidateDailyBenchmarkRepository;

  private CandidateBenchmarkQueryService candidateBenchmarkQueryService;

  @BeforeEach
  void setUp() {
    candidateBenchmarkQueryService = new CandidateBenchmarkQueryService(
        candidateDailyBenchmarkRepository, new BenchmarkProperties(60, 5, 5, 1, 2, 5, 2000, 30));
  }

  @Test
  void answersTheLatestRowWithTheSkillsItUnlocks() {
    CandidateDailyBenchmarkEntity latest = row(LATEST, 61, 72);
    latest.getSkillUnlocks().add(new SkillUnlock("Kafka", 4));
    givenLatest(latest);
    givenSeriesFrom(LocalDate.parse("2026-09-04"), latest);

    CandidateBenchmark benchmark = candidateBenchmarkQueryService.benchmark(CANDIDATE_ID);

    assertThat(benchmark.snapshotOn(), is(LATEST));
    assertThat(benchmark.marketFit(), is(61));
    assertThat(benchmark.jobsScored(), is(40));
    assertThat(benchmark.aboveThresholdPct(), is(55));
    assertThat(benchmark.reachThreshold(), is(60));
    assertThat(benchmark.peerPercentile(), is(72));
    assertThat(benchmark.peerGroupSize(), is(38));
    assertThat(benchmark.peerFitP25(), is(40));
    assertThat(benchmark.peerFitMedian(), is(52));
    assertThat(benchmark.peerFitP75(), is(66));
    assertThat(benchmark.skillCoveragePct(), is(45));
    assertThat(benchmark.peerCoverageMedian(), is(38));
    assertThat(benchmark.skillUnlocks(), contains(new SkillUnlockCount("Kafka", 4)));
  }

  @Test
  void answersTheDailySeriesOfTheWindowEndingOnTheLatestRow() {
    CandidateDailyBenchmarkEntity latest = row(LATEST, 61, 72);
    givenLatest(latest);
    givenSeriesFrom(LocalDate.parse("2026-09-04"), row(LocalDate.parse("2026-10-01"), 58, null),
        latest);

    CandidateBenchmark benchmark = candidateBenchmarkQueryService.benchmark(CANDIDATE_ID);

    assertThat(benchmark.series(), contains(
        new BenchmarkPoint(LocalDate.parse("2026-10-01"), 58, 55, null, 38),
        new BenchmarkPoint(LATEST, 61, 55, 72, 38)));
  }

  @Test
  void answersAnEmptyBenchmarkBeforeTheFirstSnapshot() {
    when(candidateDailyBenchmarkRepository.findFirstByCandidateIdOrderBySnapshotOnDesc(
        CANDIDATE_ID)).thenReturn(Optional.empty());

    CandidateBenchmark benchmark = candidateBenchmarkQueryService.benchmark(CANDIDATE_ID);

    assertThat(benchmark.snapshotOn(), is(nullValue()));
    assertThat(benchmark.marketFit(), is(nullValue()));
    assertThat(benchmark.reachThreshold(), is(60));
    assertThat(benchmark.skillUnlocks(), is(empty()));
    assertThat(benchmark.series(), is(empty()));
  }

  private void givenLatest(CandidateDailyBenchmarkEntity latest) {
    when(candidateDailyBenchmarkRepository.findFirstByCandidateIdOrderBySnapshotOnDesc(
        CANDIDATE_ID)).thenReturn(Optional.of(latest));
  }

  private void givenSeriesFrom(LocalDate from, CandidateDailyBenchmarkEntity... rows) {
    when(candidateDailyBenchmarkRepository
        .findByCandidateIdAndSnapshotOnGreaterThanEqualOrderBySnapshotOnAsc(CANDIDATE_ID, from))
        .thenReturn(List.of(rows));
  }

  private static CandidateDailyBenchmarkEntity row(LocalDate snapshotOn, Integer marketFit,
      Integer peerPercentile) {
    return CandidateDailyBenchmarkEntity.builder()
        .snapshotOn(snapshotOn)
        .candidateId(CANDIDATE_ID)
        .marketFit(marketFit)
        .jobsScored(40)
        .aboveThresholdPct(55)
        .reachThreshold(60)
        .peerPercentile(peerPercentile)
        .peerGroupSize(38)
        .peerFitP25(40)
        .peerFitMedian(52)
        .peerFitP75(66)
        .skillCoveragePct(45)
        .peerCoverageMedian(38)
        .skillUnlocks(new ArrayList<>())
        .build();
  }
}
