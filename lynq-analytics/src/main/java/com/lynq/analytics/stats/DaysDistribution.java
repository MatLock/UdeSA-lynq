package com.lynq.analytics.stats;

import java.util.Collection;

public record DaysDistribution(Double median, Double p25, Double p75, int n,
    boolean insufficientData) {

  public static DaysDistribution of(Collection<? extends Number> days, int minSample) {
    Distribution distribution = Distribution.of(days);
    if (distribution.n() < minSample) {
      return new DaysDistribution(null, null, null, distribution.n(), true);
    }
    return new DaysDistribution(distribution.median(), distribution.p25(), distribution.p75(),
        distribution.n(), false);
  }
}
