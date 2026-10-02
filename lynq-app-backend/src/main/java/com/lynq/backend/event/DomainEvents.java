package com.lynq.backend.event;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.NameBasedGenerator;
import com.lynq.backend.event.payload.ApplicationSubmittedPayload;
import com.lynq.backend.event.payload.CandidateExpectedSalaryUpdatedPayload;
import com.lynq.backend.event.payload.CandidateSkillsUpdatedPayload;
import com.lynq.backend.event.payload.JobPostClosedPayload;
import com.lynq.backend.event.payload.JobPostPublishedPayload;
import com.lynq.backend.event.payload.JobPostReopenedPayload;
import com.lynq.backend.event.payload.JobPostUpdatedPayload;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSimilarityTagEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.model.UserApplicationJobEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.model.UserSimilarityTagEntity;
import com.lynq.backend.model.UserSkillsEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DomainEvents {

  private static final UUID EVENTS_NAMESPACE =
      UUID.fromString("71624a50-fb73-526d-93be-75171faa2e73");
  private static final String KEY_SEPARATOR = "|";
  private static final Comparator<String> CANONICAL_ORDER =
      Comparator.<String, String>comparing(value -> value.toLowerCase(Locale.ROOT))
          .thenComparing(Comparator.naturalOrder());

  private DomainEvents() {
  }

  public static DomainEvent jobPostPublished(JobPostEntity job, Instant occurredOn) {
    return jobPostPublished(job, namesOf(job.getSkills(), JobPostSkillEntity::getSkill),
        namesOf(job.getSimilarityTags(), JobPostSimilarityTagEntity::getSimilarityTag), occurredOn);
  }

  public static DomainEvent jobPostPublished(JobPostEntity job, Collection<String> skills,
      Collection<String> similarityTags, Instant occurredOn) {
    JobPostPublishedPayload payload = new JobPostPublishedPayload(
        job.getId(),
        job.getTitle(),
        job.getCategory(),
        nameOf(job.getWorkType()),
        nameOf(job.getJobPostSource()),
        companyIdOf(job.getCompany()),
        job.getCreatedByUser() == null ? null : job.getCreatedByUser().getId(),
        job.getSalaryRangeDown(),
        job.getSalaryRangeTop(),
        job.getSalaryCurrency(),
        canonical(skills),
        canonical(similarityTags),
        job.getCreatedOn());
    return event(DomainEventType.JOB_POST_PUBLISHED, job.getId(), occurredOn, payload,
        payload.title(), payload.category(), payload.workType(), payload.source(),
        payload.companyId(), payload.createdByUserId(), payload.salaryRangeDown(),
        payload.salaryRangeTop(), payload.salaryCurrency(), payload.skills(),
        payload.similarityTags(), payload.publishedOn());
  }

  public static DomainEvent jobPostUpdated(JobPostEntity job, Instant occurredOn) {
    JobPostUpdatedPayload payload = new JobPostUpdatedPayload(
        job.getId(),
        job.getTitle(),
        nameOf(job.getWorkType()),
        job.getSalaryRangeDown(),
        job.getSalaryRangeTop(),
        job.getSalaryCurrency(),
        canonical(namesOf(job.getSkills(), JobPostSkillEntity::getSkill)),
        canonical(namesOf(job.getSimilarityTags(), JobPostSimilarityTagEntity::getSimilarityTag)));
    return event(DomainEventType.JOB_POST_UPDATED, job.getId(), occurredOn, payload,
        truncate(occurredOn));
  }

  public static DomainEvent jobPostClosed(JobPostEntity job, Instant occurredOn) {
    JobPostClosedPayload payload = new JobPostClosedPayload(
        job.getId(),
        job.getClosedOn(),
        nameOf(job.getCloseReason()));
    return event(DomainEventType.JOB_POST_CLOSED, job.getId(), occurredOn, payload,
        job.getCreatedOn(), payload.closedOn());
  }

  public static DomainEvent jobPostReopened(JobPostEntity job, LocalDate reopenedOn,
      Instant occurredOn) {
    JobPostReopenedPayload payload = new JobPostReopenedPayload(job.getId(), reopenedOn);
    return event(DomainEventType.JOB_POST_REOPENED, job.getId(), occurredOn, payload,
        reopenedOn);
  }

  public static DomainEvent applicationSubmitted(UserApplicationJobEntity application,
      Integer lynqScore, Instant occurredOn) {
    ApplicationSubmittedPayload payload = new ApplicationSubmittedPayload(
        application.getId(),
        application.getJobPost().getId(),
        application.getUser().getId(),
        application.getAppliedOn(),
        lynqScore);
    return event(DomainEventType.APPLICATION_SUBMITTED, application.getId(), occurredOn, payload);
  }

  public static DomainEvent candidateSkillsUpdated(UserEntity user, Instant occurredOn) {
    CandidateSkillsUpdatedPayload payload = new CandidateSkillsUpdatedPayload(
        user.getId(),
        canonical(namesOf(user.getSkills(), UserSkillsEntity::getSkill)),
        canonical(namesOf(user.getSimilarityTags(), UserSimilarityTagEntity::getSimilarityTag)));
    return event(DomainEventType.CANDIDATE_SKILLS_UPDATED, user.getId(), occurredOn, payload,
        payload.skills(), payload.similarityTags());
  }

  public static DomainEvent candidateExpectedSalaryUpdated(UserEntity user, Instant occurredOn) {
    CandidateExpectedSalaryUpdatedPayload payload = new CandidateExpectedSalaryUpdatedPayload(
        user.getId(),
        user.getExpectedSalary(),
        user.getExpectedSalary() == null ? null : user.getExpectedSalaryCurrency());
    return event(DomainEventType.CANDIDATE_EXPECTED_SALARY_UPDATED, user.getId(), occurredOn,
        payload, truncate(occurredOn));
  }

  static UUID eventId(DomainEventType type, String aggregateId, Object... identity) {
    String key = Stream.concat(Stream.of(type.eventName(), aggregateId), Stream.of(identity))
        .map(String::valueOf)
        .collect(Collectors.joining(KEY_SEPARATOR));
    NameBasedGenerator generator = Generators.nameBasedGenerator(EVENTS_NAMESPACE);
    return generator.generate(key);
  }

  private static DomainEvent event(DomainEventType type, String aggregateId, Instant occurredOn,
      Object payload, Object... identity) {
    return new DomainEvent(
        eventId(type, aggregateId, identity),
        type.eventName(),
        type.aggregateType().name(),
        aggregateId,
        truncate(occurredOn),
        payload);
  }

  private static Instant truncate(Instant occurredOn) {
    return occurredOn.truncatedTo(ChronoUnit.MILLIS);
  }

  private static <T> List<String> namesOf(List<T> entities, Function<T, String> name) {
    return entities == null ? List.of() : entities.stream().map(name).toList();
  }

  private static List<String> canonical(Collection<String> values) {
    return values == null ? List.of() : values.stream().sorted(CANONICAL_ORDER).toList();
  }

  private static String companyIdOf(CompanyEntity company) {
    return company == null ? null : company.getId();
  }

  private static String nameOf(Enum<?> value) {
    return value == null ? null : value.name();
  }
}
