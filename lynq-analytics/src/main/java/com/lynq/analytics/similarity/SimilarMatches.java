package com.lynq.analytics.similarity;

import java.util.List;

public record SimilarMatches<T>(List<SimilarMatch<T>> matches, double threshold,
    boolean fallback) {

  public static <T> SimilarMatches<T> none() {
    return new SimilarMatches<>(List.of(), 0, false);
  }

  public List<T> items() {
    return matches.stream().map(SimilarMatch::item).toList();
  }

  public int size() {
    return matches.size();
  }
}
