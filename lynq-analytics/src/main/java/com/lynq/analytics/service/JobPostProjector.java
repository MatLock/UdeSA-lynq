package com.lynq.analytics.service;

import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.exceptions.InvalidDomainEventException;
import com.lynq.analytics.exceptions.UnknownJobPostException;
import com.lynq.analytics.listener.message.DomainEventMessage;
import com.lynq.analytics.listener.message.JobPostClosedPayload;
import com.lynq.analytics.listener.message.JobPostPublishedPayload;
import com.lynq.analytics.listener.message.JobPostReopenedPayload;
import com.lynq.analytics.listener.message.JobPostUpdatedPayload;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.JobPostRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Log4j2
public class JobPostProjector implements DomainEventProjector {

  public static final String JOB_POST_PUBLISHED = "JobPostPublished";
  public static final String JOB_POST_UPDATED = "JobPostUpdated";
  public static final String JOB_POST_CLOSED = "JobPostClosed";
  public static final String JOB_POST_REOPENED = "JobPostReopened";

  private static final Set<String> EVENT_TYPES =
      Set.of(JOB_POST_PUBLISHED, JOB_POST_UPDATED, JOB_POST_CLOSED, JOB_POST_REOPENED);

  private final JobPostRepository jobPostRepository;
  private final ObjectMapper objectMapper;

  public JobPostProjector(JobPostRepository jobPostRepository, ObjectMapper objectMapper) {
    this.jobPostRepository = jobPostRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(String eventType) {
    return EVENT_TYPES.contains(eventType);
  }

  @Override
  public void project(DomainEventMessage message) {
    switch (message.eventType()) {
      case JOB_POST_PUBLISHED -> onPublished(message, read(message, JobPostPublishedPayload.class));
      case JOB_POST_UPDATED -> onUpdated(message, read(message, JobPostUpdatedPayload.class));
      case JOB_POST_CLOSED -> onClosed(message, read(message, JobPostClosedPayload.class));
      case JOB_POST_REOPENED -> onReopened(message, read(message, JobPostReopenedPayload.class));
      default -> throw new IllegalArgumentException(
          "JobPostProjector does not project '" + message.eventType() + "'");
    }
  }

  private void onPublished(DomainEventMessage message, JobPostPublishedPayload payload) {
    requireJobId(message, payload.jobId());
    requireText(message, payload.title(), "title");
    requireText(message, payload.workType(), "workType");
    requireText(message, payload.source(), "source");
    requirePresent(message, payload.publishedOn(), "publishedOn");

    JobPostEntity job = jobPostRepository.findById(payload.jobId())
        .orElseGet(() -> JobPostEntity.builder().id(payload.jobId()).build());
    boolean detailsApplied = isNotBefore(message.occurredOn(), job.getDetailsOccurredOn());
    boolean statusApplied = isNotBefore(message.occurredOn(), job.getStatusOccurredOn());

    if (detailsApplied) {
      job.setTitle(payload.title());
      job.setCategory(payload.category());
      job.setWorkType(payload.workType());
      job.setSource(payload.source());
      job.setCompanyId(payload.companyId());
      job.setCreatedByUserId(payload.createdByUserId());
      job.setSalaryRangeDown(payload.salaryRangeDown());
      job.setSalaryRangeTop(payload.salaryRangeTop());
      job.setSalaryCurrency(payload.salaryCurrency());
      job.setPublishedOn(payload.publishedOn());
      job.setSynthetic(Boolean.TRUE.equals(payload.synthetic()));
      replace(job.getSkills(), payload.skills());
      replace(job.getTags(), payload.similarityTags());
      job.setDetailsOccurredOn(message.occurredOn());
    } else {
      logDiscarded(message, "details", job.getDetailsOccurredOn());
    }

    if (statusApplied) {
      job.setStatus(JobStatus.OPEN);
      job.setClosedOn(null);
      job.setCloseReason(null);
      job.setStatusOccurredOn(message.occurredOn());
    } else {
      logDiscarded(message, "status", job.getStatusOccurredOn());
    }

    if (detailsApplied || statusApplied) {
      save(message, job);
    }
  }

  private void onUpdated(DomainEventMessage message, JobPostUpdatedPayload payload) {
    requireJobId(message, payload.jobId());
    requireText(message, payload.title(), "title");
    requireText(message, payload.workType(), "workType");

    JobPostEntity job = findPublished(message, payload.jobId());
    if (!isNotBefore(message.occurredOn(), job.getDetailsOccurredOn())) {
      logDiscarded(message, "details", job.getDetailsOccurredOn());
      return;
    }

    job.setTitle(payload.title());
    job.setWorkType(payload.workType());
    job.setSalaryRangeDown(payload.salaryRangeDown());
    job.setSalaryRangeTop(payload.salaryRangeTop());
    job.setSalaryCurrency(payload.salaryCurrency());
    replace(job.getSkills(), payload.skills());
    replace(job.getTags(), payload.similarityTags());
    job.setDetailsOccurredOn(message.occurredOn());
    save(message, job);
  }

  private void onClosed(DomainEventMessage message, JobPostClosedPayload payload) {
    requireJobId(message, payload.jobId());
    requirePresent(message, payload.closedOn(), "closedOn");

    JobPostEntity job = findPublished(message, payload.jobId());
    if (!isNotBefore(message.occurredOn(), job.getStatusOccurredOn())) {
      logDiscarded(message, "status", job.getStatusOccurredOn());
      return;
    }

    job.setStatus(JobStatus.CLOSE);
    job.setClosedOn(payload.closedOn());
    job.setCloseReason(payload.closeReason());
    job.setStatusOccurredOn(message.occurredOn());
    save(message, job);
  }

  private void onReopened(DomainEventMessage message, JobPostReopenedPayload payload) {
    requireJobId(message, payload.jobId());
    requirePresent(message, payload.reopenedOn(), "reopenedOn");

    JobPostEntity job = findPublished(message, payload.jobId());
    if (!isNotBefore(message.occurredOn(), job.getStatusOccurredOn())) {
      logDiscarded(message, "status", job.getStatusOccurredOn());
      return;
    }

    job.setStatus(JobStatus.OPEN);
    job.setReopenedOn(payload.reopenedOn());
    job.setClosedOn(null);
    job.setCloseReason(null);
    job.setStatusOccurredOn(message.occurredOn());
    save(message, job);
  }

  private JobPostEntity findPublished(DomainEventMessage message, String jobId) {
    return jobPostRepository.findById(jobId).orElseThrow(() -> new UnknownJobPostException(
        "Domain event '" + message.eventId() + "' of type '" + message.eventType()
            + "' is for job post '" + jobId + "', which has not been published yet"));
  }

  private void save(DomainEventMessage message, JobPostEntity job) {
    jobPostRepository.save(job);
    log.info("message= Projected {} '{}' onto job post '{}'", message.eventType(),
        message.eventId(), job.getId());
  }

  private void logDiscarded(DomainEventMessage message, String part, Instant storedOn) {
    log.info("message= Discarding the {} of {} '{}' for job post '{}': it occurred on {}, before "
            + "the stored {} from {}", part, message.eventType(), message.eventId(),
        message.aggregateId(), message.occurredOn(), part, storedOn);
  }

  private static boolean isNotBefore(Instant occurredOn, Instant storedOn) {
    return storedOn == null || !occurredOn.isBefore(storedOn);
  }

  private static void replace(Set<String> current, List<String> values) {
    current.clear();
    if (values == null) {
      return;
    }
    Set<String> seen = new HashSet<>();
    values.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .filter(value -> seen.add(value.toLowerCase(Locale.ROOT)))
        .forEach(current::add);
  }

  private <T> T read(DomainEventMessage message, Class<T> type) {
    try {
      return objectMapper.treeToValue(message.payload(), type);
    } catch (JacksonException e) {
      throw invalid(message, "has a payload that cannot be read: " + e.getOriginalMessage());
    }
  }

  private void requireJobId(DomainEventMessage message, String jobId) {
    requireText(message, jobId, "jobId");
    if (!jobId.equals(message.aggregateId())) {
      throw invalid(message, "has jobId '" + jobId + "' but aggregateId '"
          + message.aggregateId() + "'");
    }
  }

  private void requireText(DomainEventMessage message, String value, String field) {
    if (value == null || value.isBlank()) {
      throw invalid(message, "has no " + field);
    }
  }

  private void requirePresent(DomainEventMessage message, Object value, String field) {
    if (value == null) {
      throw invalid(message, "has no " + field);
    }
  }

  private InvalidDomainEventException invalid(DomainEventMessage message, String problem) {
    return new InvalidDomainEventException(
        "Domain event '" + message.eventId() + "' of type '" + message.eventType() + "' "
            + problem);
  }
}
