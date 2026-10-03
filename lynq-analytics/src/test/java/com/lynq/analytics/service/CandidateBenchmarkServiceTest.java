package com.lynq.analytics.service;

import com.lynq.analytics.client.request.ScoreBatchRequest.ScorePair;
import com.lynq.analytics.config.BenchmarkProperties;
import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.CandidateDailyBenchmarkEntity;
import com.lynq.analytics.model.CandidateEntity;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.model.SkillUnlock;
import com.lynq.analytics.repository.CandidateRepository;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.service.BatchScorer.ProfilePair;
import com.lynq.analytics.similarity.TagWeights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateBenchmarkServiceTest {

  private static final LocalDate SNAPSHOT_ON = LocalDate.parse("2026-10-03");
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T08:00:00Z"),
      ZoneId.of("America/Argentina/Buenos_Aires"));
  private static final int REACH_THRESHOLD = 60;
  private static final List<String> BACKEND_JOBS = List.of("j1", "j2", "j3", "j4", "j5", "j6");
  private static final Map<String, Integer> ALICE_SCORES = Map.of("j1", 90, "j2", 80, "j3", 70,
      "j4", 50, "j5", 40, "j6", 30);
  private static final Map<String, Integer> ALICE_WITH_KAFKA_SCORES = Map.of("j4", 100,
      "j5", 100, "j6", 55);
  private static final Map<String, Integer> PEER_FITS = Map.of("p1", 10, "p2", 20, "p3", 70,
      "p4", 80, "p5", 90);

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private CandidateRepository candidateRepository;

  @Mock
  private TagFrequencyService tagFrequencyService;

  @Mock
  private BatchScorer batchScorer;

  @Mock
  private CandidateBenchmarkStore candidateBenchmarkStore;

  private CandidateBenchmarkService candidateBenchmarkService;

  @BeforeEach
  void setUp() {
    candidateBenchmarkService = new CandidateBenchmarkService(jobPostRepository,
        candidateRepository, tagFrequencyService, batchScorer, candidateBenchmarkStore,
        new BenchmarkProperties(REACH_THRESHOLD, 5, 5, 1, 2, 5, 2000, 90), CLOCK);
  }

  @Test
  void writesTheFitTheReachAndTheThresholdOfACandidate() {
    givenTheMarket();
    givenTheBackendScores();

    CandidateDailyBenchmarkEntity alice = rowOf(snapshot(), "alice");

    assertThat(alice.getSnapshotOn(), is(SNAPSHOT_ON));
    assertThat(alice.getJobsScored(), is(6));
    assertThat(alice.getMarketFit(), is(60));
    assertThat(alice.getAboveThresholdPct(), is(50));
    assertThat(alice.getReachThreshold(), is(REACH_THRESHOLD));
    assertThat(alice.getSkillCoveragePct(), is(50));
    assertThat(alice.getComputedOn(), is(Instant.parse("2026-10-03T08:00:00Z")));
  }

  @SuppressWarnings("unchecked")
  @Test
  void scoresACandidateOnlyAgainstTheOpenJobPostsSharingATag() {
    givenTheMarket();
    givenTheBackendScores();
    ArgumentCaptor<List<ProfilePair>> pairs = ArgumentCaptor.forClass(List.class);

    snapshot();

    verify(batchScorer, atLeastOnce()).score(pairs.capture());
    List<String> aliceJobs = jobsScoredFor(pairs.getAllValues().getFirst(), "alice");
    assertThat(aliceJobs, containsInAnyOrder(BACKEND_JOBS.toArray()));
    assertThat(jobsScoredFor(pairs.getAllValues().getFirst(), "carol"), contains("j7"));
  }

  @Test
  void withholdsTheFitTheReachAndTheCoverageBelowFiveRelevantJobPosts() {
    givenTheMarket();
    givenTheBackendScores();

    CandidateDailyBenchmarkEntity carol = rowOf(snapshot(), "carol");

    assertThat(carol.getJobsScored(), is(1));
    assertThat(carol.getMarketFit(), is(nullValue()));
    assertThat(carol.getAboveThresholdPct(), is(nullValue()));
    assertThat(carol.getSkillCoveragePct(), is(nullValue()));
  }

  @Test
  void writesNoRowForACandidateWithoutSkillsOrTagsAndAnEmptyOneWithoutTags() {
    givenTheMarket();
    givenTheBackendScores();

    List<CandidateDailyBenchmarkEntity> rows = snapshot();

    assertThat(rows.stream().map(CandidateDailyBenchmarkEntity::getCandidateId).toList(),
        not(hasItem("nobody")));
    CandidateDailyBenchmarkEntity skillsOnly = rowOf(rows, "skills-only");
    assertThat(skillsOnly.getJobsScored(), is(0));
    assertThat(skillsOnly.getMarketFit(), is(nullValue()));
    assertThat(skillsOnly.getPeerGroupSize(), is(0));
  }

  @Test
  void placesTheCandidateAmongThePeersSharingTwoTags() {
    givenTheMarket();
    givenTheBackendScores();

    CandidateDailyBenchmarkEntity alice = rowOf(snapshot(), "alice");

    assertThat(alice.getPeerGroupSize(), is(5));
    assertThat(alice.getPeerPercentile(), is(42));
    assertThat(alice.getPeerFitP25(), is(20));
    assertThat(alice.getPeerFitMedian(), is(70));
    assertThat(alice.getPeerFitP75(), is(80));
    assertThat(alice.getPeerCoverageMedian(), is(0));
  }

  @Test
  void leavesThePercentileEmptyBelowFivePeersButCountsThem() {
    givenTheMarket();
    givenTheBackendScores();

    CandidateDailyBenchmarkEntity bob = rowOf(snapshot(), "bob");

    assertThat(bob.getMarketFit(), is(65));
    assertThat(bob.getPeerGroupSize(), is(0));
    assertThat(bob.getPeerPercentile(), is(nullValue()));
    assertThat(bob.getPeerFitMedian(), is(nullValue()));
    assertThat(bob.getPeerCoverageMedian(), is(nullValue()));
  }

  @Test
  void countsTheJobPostsAMissingSkillWouldTakeAboveTheThreshold() {
    givenTheMarket();
    givenTheBackendScores();

    CandidateDailyBenchmarkEntity alice = rowOf(snapshot(), "alice");

    assertThat(alice.getSkillUnlocks().stream().map(SkillUnlock::getSkill).toList(),
        contains("Kafka"));
    assertThat(alice.getSkillUnlocks().getFirst().getJobsUnlocked(), is(2));
  }

  @SuppressWarnings("unchecked")
  @Test
  void triesAMissingSkillOnlyOnTheJobPostsAtOrBelowTheThreshold() {
    givenTheMarket();
    givenTheBackendScores();
    ArgumentCaptor<List<ProfilePair>> pairs = ArgumentCaptor.forClass(List.class);

    snapshot();

    verify(batchScorer, times(2)).score(pairs.capture());
    assertThat(jobsScoredFor(pairs.getAllValues().getLast(), "alice+kafka"),
        containsInAnyOrder("j4", "j5", "j6"));
    assertThat(jobsScoredFor(pairs.getAllValues().getLast(), "alice+java"), is(empty()));
  }

  @Test
  void writesNothingWhenTheBackendCannotScore() {
    givenTheMarket();
    when(batchScorer.score(anyList())).thenThrow(new IllegalStateException("backend is down"));

    assertThrows(IllegalStateException.class,
        () -> candidateBenchmarkService.snapshot(SNAPSHOT_ON));

    verify(candidateBenchmarkStore, never()).replace(any(), any());
  }

  @SuppressWarnings("unchecked")
  private List<CandidateDailyBenchmarkEntity> snapshot() {
    ArgumentCaptor<List<CandidateDailyBenchmarkEntity>> rows = ArgumentCaptor.forClass(List.class);
    candidateBenchmarkService.snapshot(SNAPSHOT_ON);
    verify(candidateBenchmarkStore).replace(eq(SNAPSHOT_ON), rows.capture());
    return rows.getValue();
  }

  private void givenTheMarket() {
    List<JobPostEntity> jobPosts = new ArrayList<>();
    BACKEND_JOBS.forEach(id -> jobPosts.add(jobPost(id, Set.of("Java", "Kafka"),
        Set.of("backend"))));
    jobPosts.add(jobPost("j7", Set.of("Figma"), Set.of("design")));
    when(jobPostRepository.findWithProfileByStatus(JobStatus.OPEN)).thenReturn(jobPosts);

    List<CandidateEntity> candidates = new ArrayList<>(List.of(
        candidate("alice", Set.of("Java"), Set.of("backend", "cloud")),
        candidate("bob", Set.of("Java"), Set.of("backend")),
        candidate("carol", Set.of("Figma"), Set.of("design")),
        candidate("nobody", Set.of(), Set.of()),
        candidate("skills-only", Set.of("Java"), Set.of())));
    PEER_FITS.keySet().forEach(id ->
        candidates.add(candidate(id, Set.of("Go"), Set.of("backend", "cloud"))));
    when(candidateRepository.findAllWithProfile()).thenReturn(candidates);
    when(tagFrequencyService.weights()).thenReturn(TagWeights.of(List.of()));
  }

  private void givenTheBackendScores() {
    when(batchScorer.score(anyList())).thenAnswer(invocation -> {
      List<ProfilePair> pairs = invocation.getArgument(0);
      return pairs.stream().collect(Collectors.toMap(
          pair -> new ScorePair(pair.jobPost().id(), pair.candidate().id()),
          pair -> scoreOf(pair.jobPost().id(), pair.candidate().id()),
          (first, second) -> first));
    });
  }

  private static int scoreOf(String jobId, String candidateId) {
    return switch (candidateId) {
      case "alice" -> ALICE_SCORES.get(jobId);
      case "alice+kafka" -> ALICE_WITH_KAFKA_SCORES.getOrDefault(jobId, 0);
      case "bob" -> 65;
      case "carol" -> 50;
      default -> PEER_FITS.getOrDefault(candidateId, 0);
    };
  }

  private static List<String> jobsScoredFor(List<ProfilePair> pairs, String candidateId) {
    return pairs.stream()
        .filter(pair -> pair.candidate().id().equals(candidateId))
        .map(pair -> pair.jobPost().id())
        .toList();
  }

  private static CandidateDailyBenchmarkEntity rowOf(List<CandidateDailyBenchmarkEntity> rows,
      String candidateId) {
    return rows.stream().filter(row -> row.getCandidateId().equals(candidateId)).findFirst()
        .orElseThrow();
  }

  private static JobPostEntity jobPost(String id, Set<String> skills, Set<String> tags) {
    return JobPostEntity.builder()
        .id(id)
        .title(id)
        .workType("REMOTE")
        .source("LYNQ")
        .status(JobStatus.OPEN)
        .publishedOn(SNAPSHOT_ON)
        .skills(new HashSet<>(skills))
        .tags(new HashSet<>(tags))
        .build();
  }

  private static CandidateEntity candidate(String id, Set<String> skills, Set<String> tags) {
    return CandidateEntity.builder()
        .id(id)
        .skills(new HashSet<>(skills))
        .tags(new HashSet<>(tags))
        .build();
  }
}
