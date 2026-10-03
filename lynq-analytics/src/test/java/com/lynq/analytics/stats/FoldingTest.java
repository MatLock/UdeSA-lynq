package com.lynq.analytics.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class FoldingTest {

  @Test
  void foldsCaseAccentsAndSurroundingSpaceAsTheDatabaseCollationCompares() {
    assertThat(Folding.fold("  Diseño UX "), is("diseno ux"));
    assertThat(Folding.fold("TECNOLOGÍA"), is("tecnologia"));
  }

  @Test
  void keepsTheSymbolsThatMakeASkill() {
    assertThat(Folding.fold("C#"), is("c#"));
  }

  @Test
  void picksTheMostFrequentSpellingAndBreaksTiesAlphabetically() {
    assertThat(Folding.mostFrequent(List.of("java", "Java", "Java")), is("Java"));
    assertThat(Folding.mostFrequent(List.of("java", "Java")), is("Java"));
  }
}
