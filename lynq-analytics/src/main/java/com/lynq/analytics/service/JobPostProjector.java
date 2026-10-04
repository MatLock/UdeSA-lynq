package com.lynq.analytics.service;

import static com.lynq.analytics.service.ProjectionSupport.isNotBefore;
import static com.lynq.analytics.service.ProjectionSupport.replace;
import static com.lynq.analytics.service.ProjectionSupport.requireAggregateId;
import static com.lynq.analytics.service.ProjectionSupport.requirePresent;
import static com.lynq.analytics.service.ProjectionSupport.requireText;

import com.lynq.analytics.cache.CompanyJobsCacheEvictor;
import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.exceptions.UnknownJobPostException;
import com.lynq.analytics.listener.message.DomainEventMessage;
import com.lynq.analytics.listener.message.JobPostClosedPayload;
import com.lynq.analytics.listener.message.JobPostPublishedPayload;
import com.lynq.analytics.listener.message.JobPostReopenedPayload;
import com.lynq.analytics.listener.message.JobPostUpdatedPayload;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.JobPostRepository;
import java.time.Instant;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
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
  private final CompanyJobsCacheEvictor companyJobsCacheEvictor;

  public JobPostProjector(JobPostRepository jobPostRepository, ObjectMapper objectMapper,
      CompanyJobsCacheEvictor companyJobsCacheEvictor) {
    this.jobPostRepository = jobPostRepository;
    this.objectMapper = objectMapper;
    this.companyJobsCacheEvictor = companyJobsCacheEvictor;
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
    companyJobsCacheEvictor.evictAfterCommit(job.getCreatedByUserId());
    log.info("message= Projected {} '{}' onto job post '{}'", message.eventType(),
        message.eventId(), job.getId());
  }

  private void logDiscarded(DomainEventMessage message, String part, Instant storedOn) {
    log.info("message= Discarding the {} of {} '{}' for job post '{}': it occurred on {}, before "
            + "the stored {} from {}", part, message.eventType(), message.eventId(),
        message.aggregateId(), message.occurredOn(), part, storedOn);
  }

  private <T> T read(DomainEventMessage message, Class<T> type) {
    return ProjectionSupport.read(objectMapper, message, type);
  }

  private void requireJobId(DomainEventMessage message, String jobId) {
    requireAggregateId(message, jobId, "jobId");
  }
}
