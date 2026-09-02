package com.lynq.backend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pure computation of the LyNQ score: how much of what a job asks for the candidate has. Shared by
 * the services that surface the score from either side of an application (the job owner looking at a
 * candidate, and the candidate looking at the jobs they applied to) so the algorithm lives in
 * exactly one place.
 *
 * <p>A job is described twice: by its literal skills ("Kafka") and by the generalized capability
 * tags lynq-ml derives from them ("Asynchronous Messaging"). The candidate is described the same
 * way. Matching only the literal names is too strict — a candidate who solved the same problem with
 * RabbitMQ never surfaces for a Kafka post even though the experience transfers — so the score is
 * computed against <em>both</em> descriptions and the better one wins.
 *
 * <p>Taking the best of the two rather than blending them is deliberate: the tags are not extra
 * requirements, they are the same requirements stated at the level where substitutes are
 * interchangeable. A candidate who covers the job's capabilities matches the job, whichever
 * technologies they got there with. It also means the number can never be lower than the
 * skills-only score this used to return.
 */
final class LyNQScoreCalculator {

  private LyNQScoreCalculator() {
  }

  /** Skills-only score, for the callers that have no tags to offer (e.g. a scraped job). */
  static Integer score(List<String> jobSkillNames, List<String> userSkillNames) {
    return score(jobSkillNames, List.of(), userSkillNames, List.of());
  }

  static Integer score(List<String> jobSkillNames, List<String> jobSimilarityTagNames,
      List<String> userSkillNames, List<String> userSimilarityTagNames) {
    // Everything the candidate can be matched on. Skills and tags are pooled because a job may
    // well list as a skill ("Event Streaming") what another describes as a tag.
    Set<String> candidateVocabulary = normalize(userSkillNames, userSimilarityTagNames);

    if (candidateVocabulary.isEmpty()) {
      return 0;
    }

    Set<String> jobSkills = normalize(jobSkillNames);
    Set<String> jobSimilarityTags = normalize(jobSimilarityTagNames);

    if (jobSkills.isEmpty() && jobSimilarityTags.isEmpty()) {
      return 0;
    }

    return Math.max(
        coverage(jobSkills, candidateVocabulary),
        coverage(jobSimilarityTags, candidateVocabulary));
  }

  /** Percentage of {@code required} the candidate covers; 0 when there is nothing to require. */
  private static int coverage(Set<String> required, Set<String> candidateVocabulary) {
    if (required.isEmpty()) {
      return 0;
    }

    long matches = required.stream()
        .filter(candidateVocabulary::contains)
        .count();

    return (int) Math.round((matches * 100.0) / required.size());
  }

  @SafeVarargs
  private static Set<String> normalize(List<String>... nameLists) {
    return Stream.of(nameLists)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .filter(Objects::nonNull)
        .map(name -> name.trim().toLowerCase())
        .filter(name -> !name.isEmpty())
        .collect(Collectors.toCollection(HashSet::new));
  }
}
