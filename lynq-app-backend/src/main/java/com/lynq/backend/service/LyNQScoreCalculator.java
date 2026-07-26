package com.lynq.backend.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure computation of the LyNQ score: the percentage of a job's skills that the candidate also has.
 * Shared by the services that surface the score from either side of an application (the job owner
 * looking at a candidate, and the candidate looking at the jobs they applied to) so the algorithm
 * lives in exactly one place.
 */
final class LyNQScoreCalculator {

  private LyNQScoreCalculator() {
  }

  static Integer score(List<String> jobSkillNames, List<String> userSkillNames) {
    if (userSkillNames == null || userSkillNames.isEmpty()) {
      return 0;
    }

    if (jobSkillNames == null || jobSkillNames.isEmpty()) {
      return 0;
    }

    Set<String> normalizedUserSkills = normalize(userSkillNames);
    Set<String> normalizedJobSkills = normalize(jobSkillNames);

    if (normalizedUserSkills.isEmpty()) {
      return 0;
    }

    if (normalizedJobSkills.isEmpty()) {
      return 0;
    }

    long matches = normalizedJobSkills.stream()
        .filter(normalizedUserSkills::contains)
        .count();

    return (int) Math.round((matches * 100.0) / normalizedJobSkills.size());
  }

  private static Set<String> normalize(List<String> skills) {
    return skills.stream()
        .filter(Objects::nonNull)
        .map(skill -> skill.trim().toLowerCase())
        .filter(skill -> !skill.isEmpty())
        .collect(Collectors.toSet());
  }
}
