package com.lynq.analytics.stats;

import java.util.Collection;

public record Standing(int rank, int totalApplicants, double percentile, int score,
    Double medianScore) {

  public static Standing of(int score, Collection<Integer> scores, int minApplicants) {
    int total = scores.size();
    int above = (int) scores.stream().filter(other -> other > score).count();
    int tied = (int) scores.stream().filter(other -> other == score).count();
    int below = total - above - tied;
    double percentile = 100.0 * (below + tied / 2.0) / total;
    Double medianScore = total >= minApplicants ? Distribution.of(scores).median() : null;
    return new Standing(above + 1, total, percentile, score, medianScore);
  }
}
