package com.lynq.analytics.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class DaysDistributionTest {

  @Test
  void summarisesASampleThatReachesTheMinimum() {
    DaysDistribution days = DaysDistribution.of(List.of(30L, 10L, 21L, 14L, 25L), 5);

    assertThat(days.n(), is(5));
    assertThat(days.median(), is(21.0));
    assertThat(days.p25(), is(14.0));
    assertThat(days.p75(), is(25.0));
    assertThat(days.insufficientData(), is(false));
  }

  @Test
  void withholdsTheFiguresBelowTheMinimumButKeepsTheCount() {
    DaysDistribution days = DaysDistribution.of(List.of(10L, 20L), 5);

    assertThat(days.n(), is(2));
    assertThat(days.median(), is(nullValue()));
    assertThat(days.p25(), is(nullValue()));
    assertThat(days.p75(), is(nullValue()));
    assertThat(days.insufficientData(), is(true));
  }
}
