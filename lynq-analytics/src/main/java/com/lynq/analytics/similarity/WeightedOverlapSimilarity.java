package com.lynq.analytics.similarity;

import java.util.HashSet;
import java.util.Set;

public class WeightedOverlapSimilarity implements TagSimilarity {

  @Override
  public double score(Set<String> reference, Set<String> other, TagWeights weights) {
    Set<String> shared = new HashSet<>(reference);
    shared.retainAll(other);
    return weights.sum(shared);
  }

  @Override
  public double threshold(Set<String> reference, TagWeights weights, int medianTags) {
    return medianTags * weights.median();
  }
}
