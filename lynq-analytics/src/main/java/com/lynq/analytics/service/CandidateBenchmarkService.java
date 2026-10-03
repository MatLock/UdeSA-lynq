package com.lynq.analytics.service;

import com.lynq.analytics.cache.AnalyticsCaches;
import com.lynq.analytics.client.request.ScoreBatchRequest.ScorePair;
import com.lynq.analytics.client.request.ScoreBatchRequest.ScoredProfile;
import com.lynq.analytics.config.BenchmarkProperties;
import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.CandidateDailyBenchmarkEntity;
import com.lynq.analytics.model.CandidateEntity;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.model.SkillUnlock;
import com.lynq.analytics.repository.CandidateRepository;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.service.BatchScorer.ProfilePair;
import com.lynq.analytics.similarity.TagSimilarity;
import com.lynq.analytics.similarity.TagWeights;
import com.lynq.analytics.similarity.WeightedOverlapSimilarity;
import com.lynq.analytics.stats.Distribution;
import com.lynq.analytics.stats.Folding;
import com.lynq.analytics.stats.MarketFit;
import com.lynq.analytics.stats.PercentileRank;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class CandidateBenchmarkService {

  private static final double TOLERANCE = 1e-9;
  private static final String HYPOTHETICAL_SKILL_SEPARATOR = "+";

  private final JobPostRepository jobPostRepository;
  private final CandidateRepository candidateRepository;
  private final TagFrequencyService tagFrequencyService;
  private final BatchScorer batchScorer;
  private final CandidateBenchmarkStore candidateBenchmarkStore;
  private final BenchmarkProperties properties;
  private final Clock clock;
  private final TagSimilarity overlap = new WeightedOverlapSimilarity();

  public CandidateBenchmarkService(JobPostRepository jobPostRepository,
      CandidateRepository candidateRepository, TagFrequencyService tagFrequencyService,
      BatchScorer batchScorer, CandidateBenchmarkStore candidateBenchmarkStore,
      BenchmarkProperties properties, Clock clock) {
    this.jobPostRepository = jobPostRepository;
    this.candidateRepository = candidateRepository;
    this.tagFrequencyService = tagFrequencyService;
    this.batchScorer = batchScorer;
    this.candidateBenchmarkStore = candidateBenchmarkStore;
    this.properties = properties;
    this.clock = clock;
  }

  @CacheEvict(cacheNames = AnalyticsCaches.BENCHMARK, allEntries = true)
  public int snapshot(LocalDate snapshotOn) {
    List<JobPostEntity> openJobPosts = jobPostRepository.findWithProfileByStatus(JobStatus.OPEN);
    List<CandidateEntity> candidates = candidateRepository.findAllWithProfile().stream()
        .filter(candidate -> !candidate.getSkills().isEmpty() || !candidate.getTags().isEmpty())
        .sorted(Comparator.comparing(CandidateEntity::getId))
        .toList();
    TagWeights weights = tagFrequencyService.weights();

    Map<String, List<JobPostEntity>> relevant = relevantJobPosts(candidates, openJobPosts, weights);
    Map<String, ScoredProfile> jobPostProfiles = openJobPosts.stream().collect(
        Collectors.toMap(JobPostEntity::getId, CandidateBenchmarkService::jobPostProfile));
    Map<String, ScoredProfile> candidateProfiles = candidates.stream().collect(
        Collectors.toMap(CandidateEntity::getId, CandidateBenchmarkService::candidateProfile));
    Map<ScorePair, Integer> scores = batchScorer.score(relevant.entrySet().stream()
        .flatMap(entry -> entry.getValue().stream()
            .map(jobPost -> new ProfilePair(jobPostProfiles.get(jobPost.getId()),
                candidateProfiles.get(entry.getKey()))))
        .toList());

    Map<String, CandidateMarket> markets = new HashMap<>();
    candidates.forEach(candidate -> markets.put(candidate.getId(),
        marketOf(candidate, relevant.get(candidate.getId()), scores)));
    Map<String, List<SkillUnlock>> unlocks =
        skillUnlocks(candidates, relevant, scores, jobPostProfiles);
    Map<String, List<CandidateEntity>> peers = peers(candidates, weights);

    Instant computedOn = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    List<CandidateDailyBenchmarkEntity> rows = candidates.stream()
        .map(candidate -> row(snapshotOn, candidate, markets.get(candidate.getId()),
            peers.get(candidate.getId()).stream().map(peer -> markets.get(peer.getId())).toList(),
            unlocks.getOrDefault(candidate.getId(), List.of()), computedOn))
        .toList();
    candidateBenchmarkStore.replace(snapshotOn, rows);
    log.info("message= Wrote the candidate benchmark, snapshotOn={}, candidates={}, "
        + "openJobPosts={}, pairsScored={}", snapshotOn, rows.size(), openJobPosts.size(),
        scores.size());
    return rows.size();
  }

  private Map<String, List<JobPostEntity>> relevantJobPosts(List<CandidateEntity> candidates,
      List<JobPostEntity> openJobPosts, TagWeights weights) {
    double threshold = overlap.threshold(Set.of(), weights, properties.relevanceThresholdTags());
    Map<String, Set<String>> jobPostTags = openJobPosts.stream().collect(Collectors.toMap(
        JobPostEntity::getId, jobPost -> TagWeights.normalize(jobPost.getTags())));
    Map<String, List<JobPostEntity>> jobPostsByTag = index(openJobPosts,
        jobPost -> jobPostTags.get(jobPost.getId()));

    Map<String, List<JobPostEntity>> relevant = new LinkedHashMap<>();
    for (CandidateEntity candidate : candidates) {
      Set<String> candidateTags = TagWeights.normalize(candidate.getTags());
      relevant.put(candidate.getId(), sharingAnyTag(candidateTags, jobPostsByTag).stream()
          .filter(jobPost -> admits(overlap.score(candidateTags, jobPostTags.get(jobPost.getId()),
              weights), threshold))
          .sorted(Comparator.comparing(JobPostEntity::getId))
          .toList());
    }
    return relevant;
  }

  private Map<String, List<CandidateEntity>> peers(List<CandidateEntity> candidates,
      TagWeights weights) {
    double threshold = overlap.threshold(Set.of(), weights, properties.peerThresholdTags());
    Map<String, Set<String>> candidateTags = candidates.stream().collect(Collectors.toMap(
        CandidateEntity::getId, candidate -> TagWeights.normalize(candidate.getTags())));
    Map<String, List<CandidateEntity>> candidatesByTag = index(candidates,
        candidate -> candidateTags.get(candidate.getId()));

    Map<String, List<CandidateEntity>> peers = new HashMap<>();
    for (CandidateEntity candidate : candidates) {
      Set<String> tags = candidateTags.get(candidate.getId());
      peers.put(candidate.getId(), sharingAnyTag(tags, candidatesByTag).stream()
          .filter(other -> !other.getId().equals(candidate.getId()))
          .filter(other -> admits(overlap.score(tags, candidateTags.get(other.getId()), weights),
              threshold))
          .toList());
    }
    return peers;
  }

  private CandidateMarket marketOf(CandidateEntity candidate, List<JobPostEntity> relevant,
      Map<ScorePair, Integer> scores) {
    Map<String, Integer> scoreByJobPost = new LinkedHashMap<>();
    relevant.forEach(jobPost -> scoreByJobPost.put(jobPost.getId(),
        scoreOf(scores, jobPost.getId(), candidate.getId())));
    MarketFit fit = MarketFit.of(scoreByJobPost.values(), properties.reachThreshold(),
        properties.minRelevantJobs());
    return new CandidateMarket(fit, skillCoverage(candidate, relevant));
  }

  private Integer skillCoverage(CandidateEntity candidate, List<JobPostEntity> relevant) {
    if (relevant.size() < properties.minRelevantJobs()) {
      return null;
    }
    Set<String> candidateSkills = folded(candidate.getSkills());
    long asked = 0;
    long covered = 0;
    for (JobPostEntity jobPost : relevant) {
      Set<String> jobPostSkills = folded(jobPost.getSkills());
      asked += jobPostSkills.size();
      covered += jobPostSkills.stream().filter(candidateSkills::contains).count();
    }
    return asked == 0 ? null : (int) Math.round(100.0 * covered / asked);
  }

  private Map<String, List<SkillUnlock>> skillUnlocks(List<CandidateEntity> candidates,
      Map<String, List<JobPostEntity>> relevant, Map<ScorePair, Integer> scores,
      Map<String, ScoredProfile> jobPostProfiles) {
    List<ProfilePair> pairs = new ArrayList<>();
    Map<String, UnlockTrial> trials = new HashMap<>();
    for (CandidateEntity candidate : candidates) {
      Set<String> vocabulary = folded(Stream.concat(candidate.getSkills().stream(),
          candidate.getTags().stream()).toList());
      Map<String, List<JobPostEntity>> jobPostsBySkill = new TreeMap<>();
      Map<String, List<String>> spellingsBySkill = new HashMap<>();
      relevant.get(candidate.getId()).stream()
          .filter(jobPost -> scoreOf(scores, jobPost.getId(), candidate.getId())
              <= properties.reachThreshold())
          .forEach(jobPost -> jobPost.getSkills().stream()
              .filter(Objects::nonNull)
              .filter(skill -> !skill.isBlank())
              .filter(skill -> !vocabulary.contains(Folding.fold(skill)))
              .forEach(skill -> {
                String key = Folding.fold(skill);
                jobPostsBySkill.computeIfAbsent(key, ignored -> new ArrayList<>()).add(jobPost);
                spellingsBySkill.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(skill.trim());
              }));
      jobPostsBySkill.forEach((key, jobPosts) -> {
        String spelling = Folding.mostFrequent(spellingsBySkill.get(key));
        String trialId = candidate.getId() + HYPOTHETICAL_SKILL_SEPARATOR + key;
        List<String> skills = new ArrayList<>(candidate.getSkills());
        skills.add(spelling);
        ScoredProfile trial = new ScoredProfile(trialId, skills,
            List.copyOf(candidate.getTags()));
        trials.put(trialId, new UnlockTrial(candidate.getId(), spelling));
        new LinkedHashSet<>(jobPosts)
            .forEach(jobPost -> pairs.add(
                new ProfilePair(jobPostProfiles.get(jobPost.getId()), trial)));
      });
    }
    if (pairs.isEmpty()) {
      return Map.of();
    }

    Map<String, Integer> unlockedByTrial = new HashMap<>();
    batchScorer.score(pairs).forEach((pair, score) -> {
      if (score > properties.reachThreshold()) {
        unlockedByTrial.merge(pair.candidateId(), 1, Integer::sum);
      }
    });
    return unlockedByTrial.entrySet().stream()
        .map(entry -> new CandidateUnlock(trials.get(entry.getKey()).candidateId(),
            new SkillUnlock(trials.get(entry.getKey()).skill(), entry.getValue())))
        .collect(Collectors.groupingBy(CandidateUnlock::candidateId, Collectors.collectingAndThen(
            Collectors.toList(), unlocks -> unlocks.stream()
                .map(CandidateUnlock::unlock)
                .sorted(Comparator.comparingInt(SkillUnlock::getJobsUnlocked).reversed()
                    .thenComparing(SkillUnlock::getSkill))
                .limit(properties.skillUnlocks())
                .collect(Collectors.toCollection(ArrayList::new)))));
  }

  private CandidateDailyBenchmarkEntity row(LocalDate snapshotOn, CandidateEntity candidate,
      CandidateMarket market, List<CandidateMarket> peerMarkets, List<SkillUnlock> unlocks,
      Instant computedOn) {
    List<Integer> peerFits = peerMarkets.stream()
        .map(peer -> peer.fit().fit())
        .filter(Objects::nonNull)
        .toList();
    List<Integer> peerCoverages = peerMarkets.stream()
        .filter(peer -> peer.fit().fit() != null)
        .map(CandidateMarket::skillCoveragePct)
        .filter(Objects::nonNull)
        .toList();
    boolean enoughPeers = peerFits.size() >= properties.minPeers();
    Integer ownFit = market.fit().fit();
    Distribution peerFitDistribution = enoughPeers ? Distribution.of(peerFits) : null;

    return CandidateDailyBenchmarkEntity.builder()
        .snapshotOn(snapshotOn)
        .candidateId(candidate.getId())
        .marketFit(ownFit)
        .jobsScored(market.fit().jobsScored())
        .aboveThresholdPct(market.fit().aboveThresholdPct())
        .reachThreshold(properties.reachThreshold())
        .peerPercentile(enoughPeers && ownFit != null
            ? (int) Math.round(PercentileRank.of(ownFit, withOwn(peerFits, ownFit)))
            : null)
        .peerGroupSize(peerFits.size())
        .peerFitP25(rounded(peerFitDistribution, Distribution::p25))
        .peerFitMedian(rounded(peerFitDistribution, Distribution::median))
        .peerFitP75(rounded(peerFitDistribution, Distribution::p75))
        .skillCoveragePct(market.skillCoveragePct())
        .peerCoverageMedian(peerCoverages.size() >= properties.minPeers()
            ? (int) Math.round(Distribution.of(peerCoverages).median())
            : null)
        .computedOn(computedOn)
        .skillUnlocks(new ArrayList<>(unlocks))
        .build();
  }

  private static boolean admits(double score, double threshold) {
    return score > 0 && score >= threshold - TOLERANCE;
  }

  private static <T> Map<String, List<T>> index(List<T> items,
      Function<T, Set<String>> tagsOf) {
    Map<String, List<T>> byTag = new HashMap<>();
    items.forEach(item -> tagsOf.apply(item)
        .forEach(tag -> byTag.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(item)));
    return byTag;
  }

  private static <T> Set<T> sharingAnyTag(Set<String> tags, Map<String, List<T>> byTag) {
    Set<T> sharing = new LinkedHashSet<>();
    tags.forEach(tag -> sharing.addAll(byTag.getOrDefault(tag, List.of())));
    return sharing;
  }

  private static int scoreOf(Map<ScorePair, Integer> scores, String jobId, String candidateId) {
    Integer score = scores.get(new ScorePair(jobId, candidateId));
    if (score == null) {
      throw new IllegalStateException(
          "lynq-app-backend did not score job post '" + jobId + "' for '" + candidateId + "'");
    }
    return score;
  }

  private static Set<String> folded(Collection<String> values) {
    return values.stream()
        .filter(Objects::nonNull)
        .map(Folding::fold)
        .filter(value -> !value.isEmpty())
        .collect(Collectors.toCollection(HashSet::new));
  }

  private static List<Integer> withOwn(List<Integer> peerFits, int ownFit) {
    List<Integer> population = new ArrayList<>(peerFits);
    population.add(ownFit);
    return population;
  }

  private static Integer rounded(Distribution distribution,
      Function<Distribution, Double> statistic) {
    return distribution == null ? null : (int) Math.round(statistic.apply(distribution));
  }

  private static ScoredProfile jobPostProfile(JobPostEntity jobPost) {
    return new ScoredProfile(jobPost.getId(), List.copyOf(jobPost.getSkills()),
        List.copyOf(jobPost.getTags()));
  }

  private static ScoredProfile candidateProfile(CandidateEntity candidate) {
    return new ScoredProfile(candidate.getId(), List.copyOf(candidate.getSkills()),
        List.copyOf(candidate.getTags()));
  }

  private record CandidateMarket(MarketFit fit, Integer skillCoveragePct) {
  }

  private record UnlockTrial(String candidateId, String skill) {
  }

  private record CandidateUnlock(String candidateId, SkillUnlock unlock) {
  }
}
