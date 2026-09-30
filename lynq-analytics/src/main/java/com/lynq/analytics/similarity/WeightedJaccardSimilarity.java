package com.lynq.analytics.similarity;

import java.util.HashSet;
import java.util.Set;

public class WeightedJaccardSimilarity implements TagSimilarity {

  @Override
  public double score(Set<String> reference, Set<String> other, TagWeights weights) {
    Set<String> union = new HashSet<>(reference);
    union.addAll(other);
    double unionWeight = weights.sum(union);
    if (unionWeight == 0) {
      return 0;
    }
    Set<String> shared = new HashSet<>(reference);
    shared.retainAll(other);
    return weights.sum(shared) / unionWeight;
  }

  @Override
  public double threshold(Set<String> reference, TagWeights weights, int medianTags) {
    double referenceWeight = weights.sum(reference);
    if (referenceWeight == 0) {
      return 1;
    }
    return Math.min(1, medianTags * weights.median() / referenceWeight);
  }
}
