package com.lynq.analytics.stats;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class DistributionTest {

  @Test
  void anEmptySampleHasNoStatistics() {
    Distribution distribution = Distribution.of(List.of());

    assertThat(distribution.n(), is(0));
    assertThat(distribution.median(), is(nullValue()));
    assertThat(distribution.p25(), is(nullValue()));
    assertThat(distribution.p75(), is(nullValue()));
  }

  @Test
  void aSingleValueIsEveryPercentile() {
    Distribution distribution = Distribution.of(List.of(21));

    assertThat(distribution.n(), is(1));
    assertThat(distribution.median(), is(21.0));
    assertThat(distribution.p25(), is(21.0));
    assertThat(distribution.p75(), is(21.0));
  }

  @Test
  void theMedianOfAnOddSampleIsItsMiddleValueInWhateverOrderItArrives() {
    assertThat(Distribution.of(List.of(30, 10, 20)).median(), is(20.0));
  }

  @Test
  void theMedianOfAnEvenSampleAveragesTheTwoMiddleValues() {
    assertThat(Distribution.of(List.of(10, 20, 30, 40)).median(), is(25.0));
  }

  @Test
  void quartilesInterpolateBetweenValues() {
    Distribution distribution = Distribution.of(List.of(10, 20, 30, 40));

    assertThat(distribution.p25(), is(17.5));
    assertThat(distribution.p75(), is(32.5));
  }

  @Test
  void anOutlierMovesTheMedianLessThanItWouldTheMean() {
    assertThat(Distribution.of(List.of(1_000_000, 1_100_000, 1_200_000, 90_000_000)).median(),
        is(1_150_000.0));
  }

  @Test
  void ignoresMissingValues() {
    assertThat(Distribution.of(Arrays.asList(10, null, 30)).n(), is(2));
  }
}
