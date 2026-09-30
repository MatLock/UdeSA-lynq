package com.lynq.analytics.similarity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.lynq.analytics.similarity.PlanExample.A;
import static com.lynq.analytics.similarity.PlanExample.B;
import static com.lynq.analytics.similarity.PlanExample.MINE;
import static com.lynq.analytics.similarity.PlanExample.WEIGHTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;

class WeightedOverlapSimilarityTest {

  private final WeightedOverlapSimilarity similarity = new WeightedOverlapSimilarity();

  @Test
  void scoresThePlanExample() {
    assertThat(similarity.score(MINE, A, WEIGHTS), closeTo(2.81, 1e-9));
    assertThat(similarity.score(MINE, B, WEIGHTS), closeTo(2.52, 1e-9));
  }

  @Test
  void extraTagsOfTheCandidateDoNotLowerTheScore() {
    Set<String> wide = Set.of("backend development", "sql", "teamwork", "security", "networking");

    assertThat(similarity.score(MINE, wide, WEIGHTS), closeTo(similarity.score(MINE, A, WEIGHTS),
        1e-9));
  }

  @Test
  void nothingSharedScoresZero() {
    assertThat(similarity.score(MINE, Set.of("monitoring"), WEIGHTS), is(0.0));
  }

  @Test
  void theThresholdIsMedianTagsInAbsoluteWeight() {
    assertThat(similarity.threshold(MINE, WEIGHTS, 2), closeTo(2.40, 1e-9));
    assertThat(similarity.threshold(MINE, WEIGHTS, 1), closeTo(1.20, 1e-9));
  }
}
