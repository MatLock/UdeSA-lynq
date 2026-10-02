package com.lynq.analytics.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class StandingTest {

  private static final int MIN_APPLICANTS = 5;

  @Test
  void ranksTheCallerByTheApplicantsWhoScoredHigher() {
    Standing standing = Standing.of(70, List.of(90, 80, 70, 60, 50), MIN_APPLICANTS);

    assertThat(standing.rank(), is(3));
    assertThat(standing.totalApplicants(), is(5));
    assertThat(standing.score(), is(70));
  }

  @Test
  void tiedApplicantsShareTheirRankAndTheNextOneSkipsIt() {
    List<Integer> scores = List.of(90, 80, 80, 80, 60);

    assertThat(Standing.of(80, scores, MIN_APPLICANTS).rank(), is(2));
    assertThat(Standing.of(60, scores, MIN_APPLICANTS).rank(), is(5));
  }

  @Test
  void thePercentileCountsTheApplicantsBelowAndHalfOfThoseTied() {
    List<Integer> scores = List.of(90, 80, 80, 80, 60);

    assertThat(Standing.of(80, scores, MIN_APPLICANTS).percentile(),
        closeTo(100.0 * (1 + 1.5) / 5, 1e-9));
    assertThat(Standing.of(90, scores, MIN_APPLICANTS).percentile(),
        closeTo(100.0 * 4.5 / 5, 1e-9));
    assertThat(Standing.of(60, scores, MIN_APPLICANTS).percentile(),
        closeTo(100.0 * 0.5 / 5, 1e-9));
  }

  @Test
  void givesTheMedianScoreOfTheJobPostFromFiveApplicants() {
    assertThat(Standing.of(70, List.of(90, 80, 70, 60, 50), MIN_APPLICANTS).medianScore(),
        is(70.0));
    assertThat(Standing.of(70, List.of(90, 80, 70, 60, 50, 40), MIN_APPLICANTS).medianScore(),
        is(65.0));
  }

  @Test
  void withholdsTheMedianBelowFiveApplicantsSinceItWouldGiveTheOthersScoresAway() {
    Standing standing = Standing.of(70, List.of(70, 40), MIN_APPLICANTS);

    assertThat(standing.medianScore(), is(nullValue()));
    assertThat(standing.rank(), is(1));
    assertThat(standing.totalApplicants(), is(2));
  }

  @Test
  void aLoneApplicantIsFirstOfOne() {
    Standing standing = Standing.of(55, List.of(55), MIN_APPLICANTS);

    assertThat(standing.rank(), is(1));
    assertThat(standing.totalApplicants(), is(1));
    assertThat(standing.percentile(), is(50.0));
    assertThat(standing.medianScore(), is(nullValue()));
  }
}
