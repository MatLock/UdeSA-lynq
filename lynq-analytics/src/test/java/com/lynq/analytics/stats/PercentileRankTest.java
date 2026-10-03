package com.lynq.analytics.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PercentileRankTest {

  @Test
  void countsTheValuesBelowAndHalfOfTheTiedOnesTheValueIncluded() {
    assertThat(PercentileRank.of(72, List.of(40, 55, 72, 72, 90)), is(60.0));
  }

  @Test
  void placesTheOnlyValueInTheMiddle() {
    assertThat(PercentileRank.of(50, List.of(50)), is(50.0));
  }

  @Test
  void placesTheHighestValueNearTheTop() {
    assertThat(PercentileRank.of(90, List.of(10, 20, 30, 40, 90)), is(90.0));
  }
}
