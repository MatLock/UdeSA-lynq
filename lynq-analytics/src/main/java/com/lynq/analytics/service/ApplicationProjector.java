package com.lynq.analytics.service;

import static com.lynq.analytics.service.ProjectionSupport.invalid;
import static com.lynq.analytics.service.ProjectionSupport.isNotBefore;
import static com.lynq.analytics.service.ProjectionSupport.requireAggregateId;
import static com.lynq.analytics.service.ProjectionSupport.requirePresent;
import static com.lynq.analytics.service.ProjectionSupport.requireText;

import com.lynq.analytics.exceptions.UnknownJobPostException;
import com.lynq.analytics.listener.message.ApplicationSubmittedPayload;
import com.lynq.analytics.listener.message.DomainEventMessage;
import com.lynq.analytics.model.ApplicationEntity;
import com.lynq.analytics.repository.ApplicationRepository;
import com.lynq.analytics.repository.JobPostRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@Log4j2
public class ApplicationProjector implements DomainEventProjector {

  public static final String APPLICATION_SUBMITTED = "ApplicationSubmitted";

  private static final int MIN_LYNQ_SCORE = 0;
  private static final int MAX_LYNQ_SCORE = 100;

  private final ApplicationRepository applicationRepository;
  private final JobPostRepository jobPostRepository;
  private final ObjectMapper objectMapper;

  public ApplicationProjector(ApplicationRepository applicationRepository,
      JobPostRepository jobPostRepository, ObjectMapper objectMapper) {
    this.applicationRepository = applicationRepository;
    this.jobPostRepository = jobPostRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(String eventType) {
    return APPLICATION_SUBMITTED.equals(eventType);
  }

  @Override
  public void project(DomainEventMessage message) {
    if (!supports(message.eventType())) {
      throw new IllegalArgumentException(
          "ApplicationProjector does not project '" + message.eventType() + "'");
    }
    onSubmitted(message,
        ProjectionSupport.read(objectMapper, message, ApplicationSubmittedPayload.class));
  }

  private void onSubmitted(DomainEventMessage message, ApplicationSubmittedPayload payload) {
    requireAggregateId(message, payload.applicationId(), "applicationId");
    requireText(message, payload.jobId(), "jobId");
    requireText(message, payload.userId(), "userId");
    requirePresent(message, payload.appliedOn(), "appliedOn");
    requirePresent(message, payload.lynqScore(), "lynqScore");
    if (payload.lynqScore() < MIN_LYNQ_SCORE || payload.lynqScore() > MAX_LYNQ_SCORE) {
      throw invalid(message, "has lynqScore " + payload.lynqScore() + ", outside "
          + MIN_LYNQ_SCORE + " to " + MAX_LYNQ_SCORE);
    }
    if (!jobPostRepository.existsById(payload.jobId())) {
      throw new UnknownJobPostException("Domain event '" + message.eventId() + "' of type '"
          + message.eventType() + "' is for job post '" + payload.jobId()
          + "', which has not been published yet");
    }

    ApplicationEntity application = applicationRepository.findById(payload.applicationId())
        .orElseGet(() -> ApplicationEntity.builder().id(payload.applicationId()).build());
    if (!isNotBefore(message.occurredOn(), application.getOccurredOn())) {
      log.info("message= Discarding {} '{}' for application '{}': it occurred on {}, before the "
              + "stored one from {}", message.eventType(), message.eventId(), application.getId(),
          message.occurredOn(), application.getOccurredOn());
      return;
    }

    application.setJobId(payload.jobId());
    application.setCandidateId(payload.userId());
    application.setAppliedOn(payload.appliedOn());
    application.setLynqScore(payload.lynqScore());
    application.setOccurredOn(message.occurredOn());
    applicationRepository.save(application);
    log.info("message= Projected {} '{}' onto application '{}' of candidate '{}' for job post '{}'",
        message.eventType(), message.eventId(), application.getId(), application.getCandidateId(),
        application.getJobId());
  }
}
