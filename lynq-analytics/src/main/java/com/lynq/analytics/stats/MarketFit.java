package com.lynq.analytics.stats;

import java.util.Collection;

public record MarketFit(Integer fit, int jobsScored, Integer aboveThresholdPct) {

  public static MarketFit of(Collection<Integer> scores, int reachThreshold, int minJobs) {
    int jobsScored = scores.size();
    if (jobsScored < minJobs) {
      return new MarketFit(null, jobsScored, null);
    }
    long above = scores.stream().filter(score -> score > reachThreshold).count();
    return new MarketFit(
        (int) Math.round(Distribution.of(scores).median()),
        jobsScored,
        (int) Math.round(100.0 * above / jobsScored));
  }
}
