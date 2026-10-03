package com.lynq.analytics.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class SalaryDistributionTest {

  @Test
  void summarisesASampleOfAtLeastTheMinimumWithItsCurrency() {
    SalaryDistribution distribution = SalaryDistribution.of(List.of(600, 150, 300, 200, 400),
        "ARS", 5);

    assertThat(distribution.median(), is(300.0));
    assertThat(distribution.p25(), is(200.0));
    assertThat(distribution.p75(), is(400.0));
    assertThat(distribution.n(), is(5));
    assertThat(distribution.currency(), is("ARS"));
    assertThat(distribution.insufficientData(), is(false));
  }

  @Test
  void withholdsTheStatisticsBelowTheMinimumButKeepsTheCount() {
    SalaryDistribution distribution = SalaryDistribution.of(List.of(1000, 2000), "USD", 5);

    assertThat(distribution.median(), is(nullValue()));
    assertThat(distribution.p25(), is(nullValue()));
    assertThat(distribution.p75(), is(nullValue()));
    assertThat(distribution.n(), is(2));
    assertThat(distribution.currency(), is("USD"));
    assertThat(distribution.insufficientData(), is(true));
  }

  @Test
  void anEmptySampleIsInsufficient() {
    SalaryDistribution distribution = SalaryDistribution.of(List.of(), "ARS", 5);

    assertThat(distribution.n(), is(0));
    assertThat(distribution.insufficientData(), is(true));
  }
}
