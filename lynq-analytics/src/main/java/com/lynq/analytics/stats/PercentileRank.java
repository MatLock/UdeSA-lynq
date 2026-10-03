package com.lynq.analytics.stats;

import java.util.Collection;

public final class PercentileRank {

  private PercentileRank() {
  }

  public static double of(double value, Collection<? extends Number> population) {
    int total = population.size();
    long above = population.stream().filter(other -> other.doubleValue() > value).count();
    long tied = population.stream().filter(other -> other.doubleValue() == value).count();
    long below = total - above - tied;
    return 100.0 * (below + tied / 2.0) / total;
  }
}
