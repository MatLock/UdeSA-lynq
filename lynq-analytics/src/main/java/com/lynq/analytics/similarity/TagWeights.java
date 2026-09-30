package com.lynq.analytics.similarity;

import com.lynq.analytics.model.TagFrequencyEntity;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class TagWeights {

  private static final double NEUTRAL_WEIGHT = 1.0;

  private final Map<String, Double> weights;
  private final double unknownWeight;
  private final double median;

  private TagWeights(Map<String, Double> weights, double unknownWeight, double median) {
    this.weights = weights;
    this.unknownWeight = unknownWeight;
    this.median = median;
  }

  public static TagWeights of(Collection<TagFrequencyEntity> frequencies) {
    if (frequencies.isEmpty()) {
      return new TagWeights(Map.of(), NEUTRAL_WEIGHT, NEUTRAL_WEIGHT);
    }
    Map<String, Double> weights = frequencies.stream().collect(Collectors.toMap(
        frequency -> normalize(frequency.getTag()), TagFrequencyEntity::getWeight, Math::max));
    double unknownWeight = weights.values().stream().mapToDouble(Double::doubleValue).max()
        .orElse(NEUTRAL_WEIGHT);
    return new TagWeights(Map.copyOf(weights), unknownWeight, medianByOccurrence(frequencies));
  }

  public static Set<String> normalize(Collection<String> tags) {
    return tags.stream()
        .filter(Objects::nonNull)
        .map(TagWeights::normalize)
        .filter(tag -> !tag.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  public double weight(String tag) {
    return weights.getOrDefault(tag, unknownWeight);
  }

  public double sum(Collection<String> tags) {
    return tags.stream().mapToDouble(this::weight).sum();
  }

  public double median() {
    return median;
  }

  private static String normalize(String tag) {
    return tag.trim().toLowerCase(Locale.ROOT);
  }

  private static double medianByOccurrence(Collection<TagFrequencyEntity> frequencies) {
    List<TagFrequencyEntity> byWeight = frequencies.stream()
        .filter(frequency -> frequency.getDf() > 0)
        .sorted(Comparator.comparingDouble(TagFrequencyEntity::getWeight))
        .toList();
    long occurrences = byWeight.stream().mapToLong(TagFrequencyEntity::getDf).sum();
    if (occurrences == 0) {
      return NEUTRAL_WEIGHT;
    }
    return (weightAt(byWeight, (occurrences - 1) / 2) + weightAt(byWeight, occurrences / 2)) / 2;
  }

  private static double weightAt(List<TagFrequencyEntity> byWeight, long occurrence) {
    long seen = 0;
    for (TagFrequencyEntity frequency : byWeight) {
      seen += frequency.getDf();
      if (occurrence < seen) {
        return frequency.getWeight();
      }
    }
    return byWeight.getLast().getWeight();
  }
}
