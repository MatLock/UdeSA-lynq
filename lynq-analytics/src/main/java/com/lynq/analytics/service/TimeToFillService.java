package com.lynq.analytics.service;

import com.lynq.analytics.cache.AnalyticsCaches;
import com.lynq.analytics.config.TimeToFillProperties;
import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.exceptions.ForbiddenException;
import com.lynq.analytics.exceptions.NotFoundException;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.stats.DaysDistribution;
import com.lynq.analytics.stats.TimeToFill;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeToFillService {

  static final String EXPIRED_BY_POLICY = "EXPIRED_BY_POLICY";
  static final String LYNQ_SOURCE = "LYNQ";

  private static final String NOT_OWNER =
      "Only the company that published the job post can read its time to fill";

  private final JobPostRepository jobPostRepository;
  private final SimilarityService similarityService;
  private final TimeToFillProperties properties;
  private final Clock clock;

  public TimeToFillService(JobPostRepository jobPostRepository,
      SimilarityService similarityService, TimeToFillProperties properties, Clock clock) {
    this.jobPostRepository = jobPostRepository;
    this.similarityService = similarityService;
    this.properties = properties;
    this.clock = clock;
  }

  @Cacheable(cacheNames = AnalyticsCaches.TIME_TO_FILL, key = "#jobId + ':' + #userId")
  @Transactional(readOnly = true)
  public TimeToFill timeToFill(String jobId, String userId) {
    JobPostEntity reference = jobPostRepository.findById(jobId)
        .orElseThrow(() -> new NotFoundException("Job post '" + jobId + "' not found"));
    if (!Objects.equals(userId, reference.getCreatedByUserId())) {
      throw new ForbiddenException(NOT_OWNER);
    }

    List<JobPostEntity> closed = similarityService
        .findSimilarJobPosts(jobId, TimeToFillService::isClosed)
        .items();
    List<JobPostEntity> observed = closed.stream()
        .filter(Predicate.not(TimeToFillService::isCensored))
        .filter(post -> daysToClose(post) != null)
        .toList();
    DaysDistribution similar = DaysDistribution.of(
        observed.stream().map(TimeToFillService::daysToClose).toList(), properties.minSample());

    return TimeToFill.of(
        similar,
        (int) observed.stream().filter(TimeToFillService::isExternal).count(),
        (int) closed.stream().filter(TimeToFillService::isCensored).count(),
        properties.expiredAfterDays(),
        daysOpen(reference),
        similar.insufficientData() ? overall(jobId) : null);
  }

  private DaysDistribution overall(String jobId) {
    List<Long> days = jobPostRepository.findByStatusAndClosedOnIsNotNull(JobStatus.CLOSE).stream()
        .filter(post -> !post.getId().equals(jobId))
        .filter(Predicate.not(TimeToFillService::isCensored))
        .map(TimeToFillService::daysToClose)
        .filter(Objects::nonNull)
        .toList();
    return DaysDistribution.of(days, properties.minSample());
  }

  private long daysOpen(JobPostEntity reference) {
    LocalDate until = reference.getStatus() == JobStatus.CLOSE && reference.getClosedOn() != null
        ? reference.getClosedOn()
        : LocalDate.now(clock);
    return Math.max(0, ChronoUnit.DAYS.between(openedOn(reference), until));
  }

  static boolean isClosed(JobPostEntity post) {
    return post.getStatus() == JobStatus.CLOSE && post.getClosedOn() != null;
  }

  static boolean isCensored(JobPostEntity post) {
    return EXPIRED_BY_POLICY.equals(post.getCloseReason());
  }

  static boolean isExternal(JobPostEntity post) {
    return !LYNQ_SOURCE.equals(post.getSource());
  }

  static Long daysToClose(JobPostEntity post) {
    long days = ChronoUnit.DAYS.between(openedOn(post), post.getClosedOn());
    return days < 0 ? null : days;
  }

  static LocalDate openedOn(JobPostEntity post) {
    LocalDate reopenedOn = post.getReopenedOn();
    if (reopenedOn != null && reopenedOn.isAfter(post.getPublishedOn())) {
      return reopenedOn;
    }
    return post.getPublishedOn();
  }
}
