package com.lynq.analytics.similarity;

import com.lynq.analytics.model.TagFrequencyEntity;
import java.util.List;
import java.util.Set;

final class PlanExample {

  static final Set<String> MINE =
      Set.of("backend development", "sql", "cloud infrastructure", "teamwork");
  static final Set<String> A =
      Set.of("backend development", "sql", "teamwork", "frontend development");
  static final Set<String> B = Set.of("cloud infrastructure", "kubernetes operations", "teamwork",
      "security", "networking", "monitoring");

  static final List<TagFrequencyEntity> FREQUENCIES = List.of(
      frequency("Teamwork", 800, 0.22),
      frequency("Backend Development", 300, 1.20),
      frequency("SQL", 250, 1.39),
      frequency("Frontend Development", 200, 1.61),
      frequency("Cloud Infrastructure", 100, 2.30),
      frequency("Security", 60, 2.81),
      frequency("Monitoring", 50, 3.00),
      frequency("Networking", 40, 3.22),
      frequency("Kubernetes Operations", 20, 3.91));

  static final TagWeights WEIGHTS = TagWeights.of(FREQUENCIES);

  private PlanExample() {
  }

  static TagFrequencyEntity frequency(String tag, int df, double weight) {
    return TagFrequencyEntity.builder().tag(tag).df(df).weight(weight).build();
  }
}
