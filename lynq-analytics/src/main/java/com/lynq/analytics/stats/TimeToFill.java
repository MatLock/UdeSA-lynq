package com.lynq.analytics.stats;

public record TimeToFill(Double median, Double p25, Double p75, int n, boolean insufficientData,
    int externalJobPosts, int expiredByPolicy, int expiredAfterDays, long daysOpen,
    DaysDistribution overall) {

  public static TimeToFill of(DaysDistribution similar, int externalJobPosts,
      int expiredByPolicy, int expiredAfterDays, long daysOpen, DaysDistribution overall) {
    return new TimeToFill(similar.median(), similar.p25(), similar.p75(), similar.n(),
        similar.insufficientData(), externalJobPosts, expiredByPolicy, expiredAfterDays,
        daysOpen, overall);
  }
}
