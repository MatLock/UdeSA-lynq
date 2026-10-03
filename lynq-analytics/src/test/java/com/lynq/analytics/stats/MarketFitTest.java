package com.lynq.analytics.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class MarketFitTest {

  @Test
  void takesTheMedianScoreAndTheShareStrictlyAboveTheThreshold() {
    MarketFit fit = MarketFit.of(List.of(30, 60, 61, 80, 95), 60, 5);

    assertThat(fit.fit(), is(61));
    assertThat(fit.jobsScored(), is(5));
    assertThat(fit.aboveThresholdPct(), is(60));
  }

  @Test
  void roundsAMedianBetweenTwoScores() {
    MarketFit fit = MarketFit.of(List.of(10, 20, 30, 41, 50, 60), 60, 5);

    assertThat(fit.fit(), is(36));
    assertThat(fit.aboveThresholdPct(), is(0));
  }

  @Test
  void withholdsTheFitAndTheReachBelowTheMinimumButKeepsTheCount() {
    MarketFit fit = MarketFit.of(List.of(90, 90, 90, 90), 60, 5);

    assertThat(fit.fit(), is(nullValue()));
    assertThat(fit.aboveThresholdPct(), is(nullValue()));
    assertThat(fit.jobsScored(), is(4));
  }
}
