package com.lynq.analytics.stats;

import java.util.Collection;
import java.util.Objects;

public record Distribution(int n, Double median, Double p25, Double p75) {

  public static Distribution of(Collection<? extends Number> values) {
    double[] sorted = values.stream()
        .filter(Objects::nonNull)
        .mapToDouble(Number::doubleValue)
        .sorted()
        .toArray();
    if (sorted.length == 0) {
      return new Distribution(0, null, null, null);
    }
    return new Distribution(sorted.length, percentile(sorted, 0.5), percentile(sorted, 0.25),
        percentile(sorted, 0.75));
  }

  private static double percentile(double[] sorted, double fraction) {
    double rank = fraction * (sorted.length - 1);
    int lower = (int) Math.floor(rank);
    int upper = (int) Math.ceil(rank);
    return sorted[lower] + (rank - lower) * (sorted[upper] - sorted[lower]);
  }
}
