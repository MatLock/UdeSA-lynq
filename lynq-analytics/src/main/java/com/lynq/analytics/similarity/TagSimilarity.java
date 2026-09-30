package com.lynq.analytics.similarity;

import java.util.Set;

public interface TagSimilarity {

  double score(Set<String> reference, Set<String> other, TagWeights weights);

  double threshold(Set<String> reference, TagWeights weights, int medianTags);
}
