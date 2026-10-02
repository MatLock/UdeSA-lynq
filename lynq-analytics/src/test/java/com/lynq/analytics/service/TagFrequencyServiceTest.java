package com.lynq.analytics.service;

import com.lynq.analytics.model.TagFrequencyEntity;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.repository.TagFrequencyRepository;
import com.lynq.analytics.repository.TagFrequencyRepository.TagCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagFrequencyServiceTest {

  @Mock
  private TagFrequencyRepository tagFrequencyRepository;

  @Mock
  private JobPostRepository jobPostRepository;

  private TagFrequencyService tagFrequencyService;

  @BeforeEach
  void setUp() {
    tagFrequencyService = new TagFrequencyService(tagFrequencyRepository, jobPostRepository);
  }

  @Test
  void weighsATagByTheSmoothedLogOfItsRarity() {
    assertThat(TagFrequencyService.weight(1000, 20), closeTo(Math.log(1001.0 / 21), 1e-12));
  }

  @Test
  void aTagInEveryJobPostWeighsNothing() {
    assertThat(TagFrequencyService.weight(13, 13), is(0.0));
  }

  @Test
  void aTagSeenOnceWeighsLessThanTheUnsmoothedLog() {
    assertThat(TagFrequencyService.weight(1000, 1) < Math.log(1000), is(true));
  }

  @Test
  void recomputesEveryTagAndRemovesTheOnesNotSeenInThisRun() {
    when(jobPostRepository.count()).thenReturn(13L);
    when(tagFrequencyRepository.countJobPostTags()).thenReturn(List.of(
        count("teamwork", 13), count("kubernetes", 1)));

    int recomputed = tagFrequencyService.recompute();

    assertThat(recomputed, is(2));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<TagFrequencyEntity>> saved = ArgumentCaptor.forClass(List.class);
    verify(tagFrequencyRepository).saveAll(saved.capture());
    assertThat(saved.getValue(), hasSize(2));
    TagFrequencyEntity kubernetes = saved.getValue().get(1);
    assertThat(kubernetes.getTag(), is("kubernetes"));
    assertThat(kubernetes.getDf(), is(1));
    assertThat(kubernetes.getWeight(), closeTo(Math.log(7), 1e-12));

    ArgumentCaptor<Instant> removedBefore = ArgumentCaptor.forClass(Instant.class);
    verify(tagFrequencyRepository).deleteComputedBefore(removedBefore.capture());
    assertThat(removedBefore.getValue(), is(kubernetes.getComputedOn()));
    assertThat(removedBefore.getValue().getNano() % 1000, is(0));
  }

  @Test
  void isEmptyWithoutRows() {
    when(tagFrequencyRepository.count()).thenReturn(0L);

    assertThat(tagFrequencyService.isEmpty(), is(true));
  }

  @Test
  void loadsTheWeightsFromTheTable() {
    when(tagFrequencyRepository.findAll()).thenReturn(List.of(TagFrequencyEntity.builder()
        .tag("kubernetes").df(1).weight(1.9).build()));

    assertThat(tagFrequencyService.weights().weight("kubernetes"), is(1.9));
  }

  private static TagCount count(String tag, long df) {
    return new TagCount() {
      @Override
      public String getTag() {
        return tag;
      }

      @Override
      public long getDf() {
        return df;
      }
    };
  }
}
