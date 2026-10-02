package com.lynq.analytics.service;

import static com.lynq.analytics.service.ProjectionSupport.invalid;
import static com.lynq.analytics.service.ProjectionSupport.isNotBefore;
import static com.lynq.analytics.service.ProjectionSupport.replace;
import static com.lynq.analytics.service.ProjectionSupport.requireAggregateId;
import static com.lynq.analytics.service.ProjectionSupport.requireText;

import com.lynq.analytics.listener.message.CandidateExpectedSalaryUpdatedPayload;
import com.lynq.analytics.listener.message.CandidateSkillsUpdatedPayload;
import com.lynq.analytics.listener.message.DomainEventMessage;
import com.lynq.analytics.model.CandidateEntity;
import com.lynq.analytics.repository.CandidateRepository;
import java.time.Instant;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@Log4j2
public class CandidateProjector implements DomainEventProjector {

  public static final String CANDIDATE_SKILLS_UPDATED = "CandidateSkillsUpdated";
  public static final String CANDIDATE_EXPECTED_SALARY_UPDATED = "CandidateExpectedSalaryUpdated";

  private static final Set<String> EVENT_TYPES =
      Set.of(CANDIDATE_SKILLS_UPDATED, CANDIDATE_EXPECTED_SALARY_UPDATED);

  private final CandidateRepository candidateRepository;
  private final ObjectMapper objectMapper;

  public CandidateProjector(CandidateRepository candidateRepository, ObjectMapper objectMapper) {
    this.candidateRepository = candidateRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(String eventType) {
    return EVENT_TYPES.contains(eventType);
  }

  @Override
  public void project(DomainEventMessage message) {
    switch (message.eventType()) {
      case CANDIDATE_SKILLS_UPDATED ->
          onSkillsUpdated(message, read(message, CandidateSkillsUpdatedPayload.class));
      case CANDIDATE_EXPECTED_SALARY_UPDATED -> onExpectedSalaryUpdated(message,
          read(message, CandidateExpectedSalaryUpdatedPayload.class));
      default -> throw new IllegalArgumentException(
          "CandidateProjector does not project '" + message.eventType() + "'");
    }
  }

  private void onSkillsUpdated(DomainEventMessage message, CandidateSkillsUpdatedPayload payload) {
    requireAggregateId(message, payload.userId(), "userId");

    CandidateEntity candidate = findOrCreate(payload.userId());
    if (!isNotBefore(message.occurredOn(), candidate.getSkillsOccurredOn())) {
      logDiscarded(message, "skills", candidate.getSkillsOccurredOn());
      return;
    }

    replace(candidate.getSkills(), payload.skills());
    replace(candidate.getTags(), payload.similarityTags());
    candidate.setSkillsOccurredOn(message.occurredOn());
    save(message, candidate);
  }

  private void onExpectedSalaryUpdated(DomainEventMessage message,
      CandidateExpectedSalaryUpdatedPayload payload) {
    requireAggregateId(message, payload.userId(), "userId");
    if (payload.expectedSalary() != null) {
      if (payload.expectedSalary() <= 0) {
        throw invalid(message, "has expectedSalary " + payload.expectedSalary()
            + ", which is not positive");
      }
      requireText(message, payload.currency(), "currency");
    }

    CandidateEntity candidate = findOrCreate(payload.userId());
    if (!isNotBefore(message.occurredOn(), candidate.getSalaryOccurredOn())) {
      logDiscarded(message, "expected salary", candidate.getSalaryOccurredOn());
      return;
    }

    candidate.setExpectedSalary(payload.expectedSalary());
    candidate.setExpectedSalaryCurrency(
        payload.expectedSalary() == null ? null : payload.currency());
    candidate.setSalaryOccurredOn(message.occurredOn());
    save(message, candidate);
  }

  private CandidateEntity findOrCreate(String userId) {
    return candidateRepository.findById(userId)
        .orElseGet(() -> CandidateEntity.builder().id(userId).build());
  }

  private void save(DomainEventMessage message, CandidateEntity candidate) {
    candidateRepository.save(candidate);
    log.info("message= Projected {} '{}' onto candidate '{}'", message.eventType(),
        message.eventId(), candidate.getId());
  }

  private void logDiscarded(DomainEventMessage message, String part, Instant storedOn) {
    log.info("message= Discarding {} '{}' for candidate '{}': it occurred on {}, before the "
            + "stored {} from {}", message.eventType(), message.eventId(), message.aggregateId(),
        message.occurredOn(), part, storedOn);
  }

  private <T> T read(DomainEventMessage message, Class<T> type) {
    return ProjectionSupport.read(objectMapper, message, type);
  }
}
