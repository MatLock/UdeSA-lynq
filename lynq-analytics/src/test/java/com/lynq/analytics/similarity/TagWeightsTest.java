package com.lynq.analytics.similarity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.lynq.analytics.similarity.PlanExample.WEIGHTS;
import static com.lynq.analytics.similarity.PlanExample.frequency;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class TagWeightsTest {

  @Test
  void looksWeightsUpIgnoringCase() {
    assertThat(WEIGHTS.weight("teamwork"), is(0.22));
    assertThat(WEIGHTS.weight("cloud infrastructure"), is(2.30));
  }

  @Test
  void theMedianIsTakenOverOccurrencesNotOverDistinctTags() {
    assertThat(WEIGHTS.median(), is(1.20));
  }

  @Test
  void theMedianAveragesTheTwoMiddleOccurrences() {
    TagWeights weights = TagWeights.of(List.of(frequency("a", 1, 1.0), frequency("b", 1, 2.0)));

    assertThat(weights.median(), is(1.5));
  }

  @Test
  void aTagWithoutFrequencyWeighsAsMuchAsTheRarestKnownOne() {
    assertThat(WEIGHTS.weight("quantum computing"), is(3.91));
  }

  @Test
  void sumsTheWeightsOfATagSet() {
    assertThat(WEIGHTS.sum(Set.of("teamwork", "sql")), closeTo(1.61, 1e-9));
  }

  @Test
  void withoutFrequenciesEveryTagWeighsOneSoTheMetricsCountTags() {
    TagWeights weights = TagWeights.of(List.of());

    assertThat(weights.weight("anything"), is(1.0));
    assertThat(weights.median(), is(1.0));
  }

  @Test
  void normalizesTagsToTrimmedLowerCaseWithoutBlanks() {
    assertThat(TagWeights.normalize(Arrays.asList(" Backend ", "backend", "SQL", "", null)),
        containsInAnyOrder("backend", "sql"));
  }
}
