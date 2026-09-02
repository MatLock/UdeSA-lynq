package com.lynq.backend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class LyNQScoreCalculator {

  private LyNQScoreCalculator() {
  }

  static Integer score(List<String> jobSkillNames, List<String> userSkillNames) {
    return score(jobSkillNames, List.of(), userSkillNames, List.of());
  }

  static Integer score(List<String> jobSkillNames, List<String> jobSimilarityTagNames,
      List<String> userSkillNames, List<String> userSimilarityTagNames) {
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
