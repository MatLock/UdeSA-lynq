package com.lynq.backend.service;

import com.lynq.backend.config.VerificationProperties;
import com.lynq.backend.controller.request.LivenessReportRequest;
import com.lynq.backend.controller.response.ExpireJobPostsRestResponse;
import com.lynq.backend.controller.response.LivenessRestResponse;
import com.lynq.backend.controller.response.VerificationCandidateRestResponse;
import com.lynq.backend.controller.response.VerificationCandidatesRestResponse;
import com.lynq.backend.enums.CloseReason;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.LivenessOutcome;
import com.lynq.backend.event.DomainEventPublisher;
import com.lynq.backend.event.DomainEvents;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.repository.JobPostRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobVerificationService {

  private final JobPostRepository jobPostRepository;
  private final DomainEventPublisher domainEventPublisher;
  private final VerificationProperties properties;

  public JobVerificationService(JobPostRepository jobPostRepository,
      DomainEventPublisher domainEventPublisher, VerificationProperties properties) {
    this.jobPostRepository = jobPostRepository;
    this.domainEventPublisher = domainEventPublisher;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public VerificationCandidatesRestResponse candidates() {
    LocalDate today = today();
    List<String> ids = jobPostRepository.findVerificationCandidateIds(
        today.minusDays(properties.windowDays()), today, properties.quotaPerCategory());
    Map<String, JobPostEntity> jobs = byId(ids);
    List<VerificationCandidateRestResponse> candidates = ids.stream()
        .map(jobs::get)
        .filter(Objects::nonNull)
        .map(JobVerificationService::toCandidate)
        .toList();
    return new VerificationCandidatesRestResponse(candidates);
  }

  @Transactional
  public LivenessRestResponse report(List<LivenessReportRequest> reports) {
    LocalDate today = today();
    Instant occurredOn = Instant.now();
    Map<String, JobPostEntity> jobs =
        byId(reports.stream().map(LivenessReportRequest::getId).distinct().toList());
    Map<LivenessOutcome, Integer> applied = new EnumMap<>(LivenessOutcome.class);
    int skipped = 0;

    for (LivenessReportRequest report : reports) {
      JobPostEntity job = jobs.get(report.getId());
      if (!isOpenExternal(job)) {
        skipped++;
        continue;
      }
      apply(job, report.getOutcome(), today, occurredOn);
      jobPostRepository.save(job);
      applied.merge(report.getOutcome(), 1, Integer::sum);
    }

    return LivenessRestResponse.builder()
        .alive(applied.getOrDefault(LivenessOutcome.ALIVE, 0))
        .closed(applied.getOrDefault(LivenessOutcome.CLOSED, 0))
        .gone(applied.getOrDefault(LivenessOutcome.GONE, 0))
        .unknown(applied.getOrDefault(LivenessOutcome.UNKNOWN, 0))
        .skipped(skipped)
        .build();
  }

  @Transactional
  public ExpireJobPostsRestResponse expire() {
    LocalDate today = today();
    Instant occurredOn = Instant.now();
    List<JobPostEntity> expired = jobPostRepository.findOpenExternalNotSeenSince(
        today.minusDays(properties.expireAfterDays()));
    expired.forEach(job -> close(job, CloseReason.EXPIRED_BY_POLICY, today, occurredOn));
    jobPostRepository.saveAll(expired);
    return new ExpireJobPostsRestResponse(expired.size());
  }

  private void apply(JobPostEntity job, LivenessOutcome outcome, LocalDate today,
      Instant occurredOn) {
    job.setLastCheckedOn(today);
    switch (outcome) {
      case ALIVE -> job.setLastSeenOn(today);
      case CLOSED -> close(job, CloseReason.VERIFIED_CLOSED, today, occurredOn);
      case GONE -> close(job, CloseReason.VERIFIED_GONE, today, occurredOn);
      case UNKNOWN -> {
      }
    }
  }

  private void close(JobPostEntity job, CloseReason reason, LocalDate today, Instant occurredOn) {
    job.setJobStatus(JobStatus.CLOSE);
    job.setClosedOn(today);
    job.setCloseReason(reason);
    domainEventPublisher.publish(DomainEvents.jobPostClosed(job, occurredOn));
  }

  private Map<String, JobPostEntity> byId(List<String> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return jobPostRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(JobPostEntity::getId, Function.identity()));
  }

  private static boolean isOpenExternal(JobPostEntity job) {
    return job != null
        && job.getJobPostSource() != JobPostSource.LYNQ
        && job.getJobStatus() == JobStatus.OPEN;
  }

  private static VerificationCandidateRestResponse toCandidate(JobPostEntity job) {
    return VerificationCandidateRestResponse.builder()
        .id(job.getId())
        .jobUrl(job.getJobUrl())
        .source(job.getJobPostSource())
        .category(job.getCategory())
        .lastSeenOn(job.getLastSeenOn())
        .lastCheckedOn(job.getLastCheckedOn())
        .build();
  }

  private static LocalDate today() {
    return LocalDate.now(ZoneOffset.UTC);
  }
}
