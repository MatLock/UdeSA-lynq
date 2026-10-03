package com.lynq.analytics.stats;

import java.util.Collection;

public record SalaryDistribution(Double median, Double p25, Double p75, int n, String currency,
    boolean insufficientData) {

  public static SalaryDistribution of(Collection<? extends Number> salaries, String currency,
      int minSample) {
    Distribution distribution = Distribution.of(salaries);
    if (distribution.n() < minSample) {
      return new SalaryDistribution(null, null, null, distribution.n(), currency, true);
    }
    return new SalaryDistribution(distribution.median(), distribution.p25(), distribution.p75(),
        distribution.n(), currency, false);
  }
}
