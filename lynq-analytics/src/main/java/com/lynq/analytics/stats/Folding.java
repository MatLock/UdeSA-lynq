package com.lynq.analytics.stats;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Folding {

  private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

  private Folding() {
  }

  public static String fold(String value) {
    String decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
    return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
  }

  public static String mostFrequent(Collection<String> spellings) {
    Map<String, Long> counts = spellings.stream()
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    return counts.entrySet().stream()
        .min(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
            .thenComparing(Map.Entry::getKey))
        .map(Map.Entry::getKey)
        .orElseThrow();
  }
}
