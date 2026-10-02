package com.lynq.analytics.service;

import com.lynq.analytics.model.TagFrequencyEntity;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.repository.TagFrequencyRepository;
import com.lynq.analytics.similarity.TagWeights;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Log4j2
public class TagFrequencyService {

  private final TagFrequencyRepository tagFrequencyRepository;
  private final JobPostRepository jobPostRepository;

  public TagFrequencyService(TagFrequencyRepository tagFrequencyRepository,
      JobPostRepository jobPostRepository) {
    this.tagFrequencyRepository = tagFrequencyRepository;
    this.jobPostRepository = jobPostRepository;
  }

  @Transactional
  public int recompute() {
    Instant computedOn = Instant.now().truncatedTo(ChronoUnit.MICROS);
    long jobPosts = jobPostRepository.count();
    List<TagFrequencyEntity> frequencies = tagFrequencyRepository.countJobPostTags().stream()
        .map(count -> TagFrequencyEntity.builder()
            .tag(count.getTag())
            .df((int) count.getDf())
            .weight(weight(jobPosts, count.getDf()))
            .computedOn(computedOn)
            .build())
        .toList();
    tagFrequencyRepository.saveAll(frequencies);
    int removed = tagFrequencyRepository.deleteComputedBefore(computedOn);
    log.info("message= Recomputed the frequency of {} tags over {} job posts, removed {} tags no "
        + "job post has anymore", frequencies.size(), jobPosts, removed);
    return frequencies.size();
  }

  @Transactional(readOnly = true)
  public boolean isEmpty() {
    return tagFrequencyRepository.count() == 0;
  }

  @Transactional(readOnly = true)
  public TagWeights weights() {
    return TagWeights.of(tagFrequencyRepository.findAll());
  }

  static double weight(long jobPosts, long df) {
    return Math.log((jobPosts + 1.0) / (df + 1.0));
  }
}
