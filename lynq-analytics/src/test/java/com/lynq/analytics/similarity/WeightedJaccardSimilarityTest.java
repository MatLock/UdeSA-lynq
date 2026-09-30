package com.lynq.analytics.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.lynq.analytics.similarity.PlanExample.A;
import static com.lynq.analytics.similarity.PlanExample.B;
import static com.lynq.analytics.similarity.PlanExample.MINE;
import static com.lynq.analytics.similarity.PlanExample.WEIGHTS;
import static com.lynq.analytics.similarity.PlanExample.frequency;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;

class WeightedJaccardSimilarityTest {

  private final WeightedJaccardSimilarity similarity = new WeightedJaccardSimilarity();

  @Test
  void scoresThePlanExample() {
    assertThat(similarity.score(MINE, A, WEIGHTS), closeTo(2.81 / 6.72, 1e-3));
    assertThat(similarity.score(MINE, B, WEIGHTS), closeTo(2.52 / 18.05, 1e-3));
  }

  @Test
  void anIdenticalTagSetScoresOne() {
    assertThat(similarity.score(MINE, MINE, WEIGHTS), closeTo(1, 1e-9));
  }

  @Test
  void aBroadPostThatContainsTheReferenceScoresBelowANarrowOne() {
    Set<String> broad = Set.of("backend development", "sql", "cloud infrastructure", "teamwork",
        "security", "networking", "monitoring", "kubernetes operations");

    assertThat(similarity.score(MINE, broad, WEIGHTS) < similarity.score(MINE, A, WEIGHTS),
        is(true));
  }

  @Test
  void aSetWithoutWeightScoresZero() {
    TagWeights weights = TagWeights.of(List.of(frequency("teamwork", 10, 0.0)));

    assertThat(similarity.score(Set.of("teamwork"), Set.of("teamwork"), weights), is(0.0));
  }

  @Test
  void theThresholdIsMedianTagsRelativeToTheReferenceWeight() {
    double referenceWeight = 1.20 + 1.39 + 2.30 + 0.22;

    assertThat(similarity.threshold(MINE, WEIGHTS, 2), closeTo(2 * 1.20 / referenceWeight, 1e-9));
    assertThat(similarity.threshold(MINE, WEIGHTS, 1), closeTo(1.20 / referenceWeight, 1e-9));
  }

  @Test
  void theThresholdNeverAsksForMoreThanAnIdenticalSet() {
    assertThat(similarity.threshold(Set.of("teamwork"), WEIGHTS, 2), is(1.0));
  }

  @Test
  void aReferenceWithoutWeightOnlyAdmitsWhatScoresOne() {
    TagWeights weights = TagWeights.of(List.of(frequency("teamwork", 10, 0.0)));

    assertThat(similarity.threshold(Set.of("teamwork"), weights, 1), is(1.0));
  }
}
