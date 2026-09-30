package com.lynq.analytics.service;

import com.lynq.analytics.config.SimilarityProperties;
import com.lynq.analytics.exceptions.NotFoundException;
import com.lynq.analytics.model.CandidateEntity;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.CandidateRepository;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.similarity.SimilarMatch;
import com.lynq.analytics.similarity.SimilarMatches;
import com.lynq.analytics.similarity.TagSimilarity;
import com.lynq.analytics.similarity.TagWeights;
import com.lynq.analytics.similarity.WeightedJaccardSimilarity;
import com.lynq.analytics.similarity.WeightedOverlapSimilarity;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimilarityService {

  private static final double TOLERANCE = 1e-9;

  private static final Comparator<SimilarMatch<?>> BY_SCORE_THEN_SHARED_SKILLS =
      Comparator.<SimilarMatch<?>>comparingDouble(SimilarMatch::score).reversed()
          .thenComparing(Comparator.<SimilarMatch<?>>comparingInt(SimilarMatch::sharedSkills)
              .reversed());

  private final JobPostRepository jobPostRepository;
  private final CandidateRepository candidateRepository;
  private final TagFrequencyService tagFrequencyService;
  private final SimilarityProperties properties;
  private final TagSimilarity jobPostSimilarity = new WeightedJaccardSimilarity();
  private final TagSimilarity candidateSimilarity = new WeightedOverlapSimilarity();

  public SimilarityService(JobPostRepository jobPostRepository,
      CandidateRepository candidateRepository, TagFrequencyService tagFrequencyService,
      SimilarityProperties properties) {
    this.jobPostRepository = jobPostRepository;
    this.candidateRepository = candidateRepository;
    this.tagFrequencyService = tagFrequencyService;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public SimilarMatches<JobPostEntity> findSimilarJobPosts(String jobId,
      Predicate<JobPostEntity> eligible) {
    JobPostEntity reference = findJobPost(jobId);
    Set<String> referenceTags = TagWeights.normalize(reference.getTags());
    if (referenceTags.isEmpty()) {
      return SimilarMatches.none();
    }
    List<JobPostEntity> others = jobPostRepository.findSharingTags(referenceTags, jobId,
        properties.includeSynthetic());
    return match(reference, referenceTags, others, JobPostEntity::getTags,
        JobPostEntity::getSkills, eligible, jobPostSimilarity);
  }

  @Transactional(readOnly = true)
  public SimilarMatches<CandidateEntity> findSimilarCandidates(String jobId,
      Predicate<CandidateEntity> eligible) {
    JobPostEntity reference = findJobPost(jobId);
    Set<String> referenceTags = TagWeights.normalize(reference.getTags());
    if (referenceTags.isEmpty()) {
      return SimilarMatches.none();
    }
    List<CandidateEntity> others = candidateRepository.findSharingTags(referenceTags,
        properties.includeSynthetic());
    return match(reference, referenceTags, others, CandidateEntity::getTags,
        CandidateEntity::getSkills, eligible, candidateSimilarity);
  }

  private <T> SimilarMatches<T> match(JobPostEntity reference, Set<String> referenceTags,
      List<T> others, Function<T, Set<String>> tags, Function<T, Set<String>> skills,
      Predicate<T> eligible, TagSimilarity similarity) {
    TagWeights weights = tagFrequencyService.weights();
    Set<String> referenceSkills = TagWeights.normalize(reference.getSkills());
    List<SimilarMatch<T>> scored = others.stream()
        .filter(eligible)
        .map(other -> new SimilarMatch<>(other,
            similarity.score(referenceTags, TagWeights.normalize(tags.apply(other)), weights),
            shared(referenceSkills, TagWeights.normalize(skills.apply(other)))))
        .filter(match -> match.score() > 0)
        .sorted(BY_SCORE_THEN_SHARED_SKILLS)
        .toList();

    double threshold = similarity.threshold(referenceTags, weights, properties.thresholdTags());
    List<SimilarMatch<T>> admitted = admitted(scored, threshold);
    if (admitted.size() >= properties.minSample()) {
      return new SimilarMatches<>(admitted, threshold, false);
    }
    double fallback = similarity.threshold(referenceTags, weights,
        properties.fallbackThresholdTags());
    return new SimilarMatches<>(admitted(scored, fallback), fallback, true);
  }

  private JobPostEntity findJobPost(String jobId) {
    return jobPostRepository.findById(jobId)
        .orElseThrow(() -> new NotFoundException("Job post '" + jobId + "' not found"));
  }

  private static <T> List<SimilarMatch<T>> admitted(List<SimilarMatch<T>> scored,
      double threshold) {
    return scored.stream().filter(match -> match.score() >= threshold - TOLERANCE).toList();
  }

  private static int shared(Set<String> reference, Set<String> other) {
    return (int) other.stream().filter(reference::contains).count();
  }
}
