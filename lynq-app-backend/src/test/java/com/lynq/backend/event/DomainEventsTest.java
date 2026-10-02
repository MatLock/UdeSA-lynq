package com.lynq.backend.event;

import com.lynq.backend.enums.CloseReason;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.event.payload.ApplicationSubmittedPayload;
import com.lynq.backend.event.payload.CandidateExpectedSalaryUpdatedPayload;
import com.lynq.backend.event.payload.CandidateSkillsUpdatedPayload;
import com.lynq.backend.event.payload.JobPostClosedPayload;
import com.lynq.backend.event.payload.JobPostPublishedPayload;
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
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

class DomainEventsTest {

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final String COMPANY_ID = "22222222-2222-2222-2222-222222222222";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String APPLICATION_ID = "33333333-3333-3333-3333-333333333333";
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2026, Month.SEPTEMBER, 20);
  private static final LocalDate CLOSED_ON = LocalDate.of(2026, Month.OCTOBER, 1);
  private static final Instant T1 = Instant.parse("2026-09-20T10:00:00.123456789Z");
  private static final Instant T2 = Instant.parse("2026-09-22T10:00:00Z");

  @Test
  void jobPostPublishedCarriesTheJobPostWithItsSkillsAndTagsInCanonicalOrder() {
    DomainEvent event = DomainEvents.jobPostPublished(
        jobPost(List.of("spring", "Java", "AWS"), List.of("Cloud", "backend")), T1);

    assertThat(event.eventType(), is("JobPostPublished"));
    assertThat(event.aggregateType(), is("JOB_POST"));
    assertThat(event.aggregateId(), is(JOB_ID));
    assertThat(event.occurredOn(), is(Instant.parse("2026-09-20T10:00:00.123Z")));
    JobPostPublishedPayload payload = (JobPostPublishedPayload) event.payload();
    assertThat(payload.jobId(), is(JOB_ID));
    assertThat(payload.title(), is("Backend Developer"));
    assertThat(payload.category(), is("tecnologia"));
    assertThat(payload.workType(), is("REMOTE"));
    assertThat(payload.source(), is("LYNQ"));
    assertThat(payload.companyId(), is(COMPANY_ID));
    assertThat(payload.createdByUserId(), is(USER_ID));
    assertThat(payload.salaryRangeDown(), is(1500000));
    assertThat(payload.salaryRangeTop(), is(2200000));
    assertThat(payload.salaryCurrency(), is("ARS"));
    assertThat(payload.skills(), contains("AWS", "Java", "spring"));
    assertThat(payload.similarityTags(), contains("backend", "Cloud"));
    assertThat(payload.publishedOn(), is(PUBLISHED_ON));
  }

  @Test
  void theSameJobPostIsTheSamePublishedEventWhateverTheOrderOrTheMoment() {
    DomainEvent first = DomainEvents.jobPostPublished(
        jobPost(List.of("Java", "Spring"), List.of("backend")), T1);
    DomainEvent again = DomainEvents.jobPostPublished(
        jobPost(List.of("Spring", "Java"), List.of("backend")), T2);

    assertThat(again.eventId(), is(first.eventId()));
    assertThat(first.eventId().version(), is(5));
  }

  @Test
  void aChangedJobPostIsANewPublishedEvent() {
    JobPostEntity changed = jobPost(List.of("Java"), List.of("backend"));
    changed.setSalaryRangeTop(2500000);

    assertThat(DomainEvents.jobPostPublished(changed, T1).eventId(),
        is(not(DomainEvents.jobPostPublished(jobPost(List.of("Java"), List.of("backend")), T1)
            .eventId())));
  }

  @Test
  void jobPostPublishedForAnExternalPostHasNoAuthorOrCompany() {
    JobPostEntity external = jobPost(List.of(), List.of());
    external.setCreatedByUser(null);
    external.setCompany(null);
    external.setJobPostSource(JobPostSource.BUMERAN);

    JobPostPublishedPayload payload =
        (JobPostPublishedPayload) DomainEvents.jobPostPublished(external, T1).payload();

    assertThat(payload.createdByUserId(), is(nullValue()));
    assertThat(payload.companyId(), is(nullValue()));
    assertThat(payload.source(), is("BUMERAN"));
  }

  @Test
  void eachUpdateIsItsOwnEvent() {
    JobPostEntity job = jobPost(List.of("Java"), List.of());

    assertThat(DomainEvents.jobPostUpdated(job, T1).eventId(),
        is(not(DomainEvents.jobPostUpdated(job, T2).eventId())));
    assertThat(DomainEvents.jobPostUpdated(job, T1).eventId(),
        is(DomainEvents.jobPostUpdated(job, T1).eventId()));
  }

  @Test
  void aCloseIsIdentifiedByTheOpenPeriodItEndsAndNotByWhenItIsSent() {
    JobPostEntity job = closedJobPost();
    DomainEvent closed = DomainEvents.jobPostClosed(job, T1);

    JobPostClosedPayload payload = (JobPostClosedPayload) closed.payload();
    assertThat(closed.eventType(), is("JobPostClosed"));
    assertThat(payload.closedOn(), is(CLOSED_ON));
    assertThat(payload.closeReason(), is("OWNER"));
    assertThat(DomainEvents.jobPostClosed(job, T2).eventId(), is(closed.eventId()));

    job.setCreatedOn(CLOSED_ON);
    assertThat(DomainEvents.jobPostClosed(job, T1).eventId(), is(not(closed.eventId())));
  }

  @Test
  void aReopeningIsIdentifiedByItsDate() {
    JobPostEntity job = jobPost(List.of(), List.of());

    assertThat(DomainEvents.jobPostReopened(job, CLOSED_ON, T1).eventId(),
        is(DomainEvents.jobPostReopened(job, CLOSED_ON, T2).eventId()));
    assertThat(DomainEvents.jobPostReopened(job, CLOSED_ON, T1).eventId(),
        is(not(DomainEvents.jobPostReopened(job, CLOSED_ON.plusDays(1), T1).eventId())));
  }

  @Test
  void anApplicationIsOneEventWhateverItsScore() {
    UserApplicationJobEntity application = UserApplicationJobEntity.builder()
        .id(APPLICATION_ID)
        .jobPost(jobPost(List.of(), List.of()))
        .user(candidate())
        .appliedOn(PUBLISHED_ON)
        .build();

    DomainEvent submitted = DomainEvents.applicationSubmitted(application, 72, T1);

    assertThat(submitted.aggregateType(), is("APPLICATION"));
    assertThat(submitted.aggregateId(), is(APPLICATION_ID));
    ApplicationSubmittedPayload payload = (ApplicationSubmittedPayload) submitted.payload();
    assertThat(payload.applicationId(), is(APPLICATION_ID));
    assertThat(payload.jobId(), is(JOB_ID));
    assertThat(payload.userId(), is(USER_ID));
    assertThat(payload.appliedOn(), is(PUBLISHED_ON));
    assertThat(payload.lynqScore(), is(72));
    assertThat(DomainEvents.applicationSubmitted(application, 40, T2).eventId(),
        is(submitted.eventId()));
  }

  @Test
  void candidateSkillsUpdatedCarriesEverySkillAndTagAndIsIdentifiedByThem() {
    UserEntity candidate = candidate();

    DomainEvent event = DomainEvents.candidateSkillsUpdated(candidate, T1);

    assertThat(event.aggregateType(), is("CANDIDATE"));
    CandidateSkillsUpdatedPayload payload = (CandidateSkillsUpdatedPayload) event.payload();
    assertThat(payload.userId(), is(USER_ID));
    assertThat(payload.skills(), contains("Java", "Kotlin"));
    assertThat(payload.similarityTags(), contains("backend"));
    assertThat(DomainEvents.candidateSkillsUpdated(candidate, T2).eventId(), is(event.eventId()));

    candidate.getSkills().add(UserSkillsEntity.builder().skill("Go").user(candidate).build());
    assertThat(DomainEvents.candidateSkillsUpdated(candidate, T1).eventId(),
        is(not(event.eventId())));
  }

  @Test
  void expectedSalaryUpdatedCarriesTheCurrencyOnlyWithASalary() {
    UserEntity candidate = candidate();
    candidate.setExpectedSalary(2000000);
    candidate.setExpectedSalaryCurrency("ARS");

    CandidateExpectedSalaryUpdatedPayload withSalary = (CandidateExpectedSalaryUpdatedPayload)
        DomainEvents.candidateExpectedSalaryUpdated(candidate, T1).payload();
    candidate.setExpectedSalary(null);
    CandidateExpectedSalaryUpdatedPayload cleared = (CandidateExpectedSalaryUpdatedPayload)
        DomainEvents.candidateExpectedSalaryUpdated(candidate, T2).payload();

    assertThat(withSalary.expectedSalary(), is(2000000));
    assertThat(withSalary.currency(), is("ARS"));
    assertThat(cleared.expectedSalary(), is(nullValue()));
    assertThat(cleared.currency(), is(nullValue()));
  }

  @Test
  void eachSalaryChangeIsItsOwnEvent() {
    UserEntity candidate = candidate();
    candidate.setExpectedSalary(2000000);
    candidate.setExpectedSalaryCurrency("ARS");

    assertThat(DomainEvents.candidateExpectedSalaryUpdated(candidate, T1).eventId(),
        is(not(DomainEvents.candidateExpectedSalaryUpdated(candidate, T2).eventId())));
  }

  @Test
  void eventIdsOfDifferentTypesNeverCollide() {
    UUID reopened = DomainEvents.eventId(DomainEventType.JOB_POST_REOPENED, JOB_ID, CLOSED_ON);
    UUID other = DomainEvents.eventId(DomainEventType.JOB_POST_PUBLISHED, JOB_ID, CLOSED_ON);

    assertThat(reopened, is(not(other)));
  }

  private static JobPostEntity jobPost(List<String> skills, List<String> tags) {
    JobPostEntity job = JobPostEntity.builder()
        .id(JOB_ID)
        .title("Backend Developer")
        .category("tecnologia")
        .workType(WorkType.REMOTE)
        .jobPostSource(JobPostSource.LYNQ)
        .company(CompanyEntity.builder().id(COMPANY_ID).build())
        .createdByUser(UserEntity.builder().id(USER_ID).build())
        .salaryRangeDown(1500000)
        .salaryRangeTop(2200000)
        .salaryCurrency("ARS")
        .createdOn(PUBLISHED_ON)
        .jobStatus(JobStatus.OPEN)
        .skills(new ArrayList<>())
        .similarityTags(new ArrayList<>())
        .build();
    skills.forEach(skill -> job.getSkills().add(
        JobPostSkillEntity.builder().skill(skill).jobPost(job).build()));
    tags.forEach(tag -> job.getSimilarityTags().add(
        JobPostSimilarityTagEntity.builder().similarityTag(tag).jobPost(job).build()));
    return job;
  }

  private static JobPostEntity closedJobPost() {
    JobPostEntity job = jobPost(List.of(), List.of());
    job.setJobStatus(JobStatus.CLOSE);
    job.setClosedOn(CLOSED_ON);
    job.setCloseReason(CloseReason.OWNER);
    return job;
  }

  private static UserEntity candidate() {
    UserEntity user = UserEntity.builder()
        .id(USER_ID)
        .createdOn(PUBLISHED_ON)
        .skills(new ArrayList<>())
        .similarityTags(new ArrayList<>())
        .build();
    user.getSkills().add(UserSkillsEntity.builder().skill("Kotlin").user(user).build());
    user.getSkills().add(UserSkillsEntity.builder().skill("Java").user(user).build());
    user.getSimilarityTags().add(
        UserSimilarityTagEntity.builder().similarityTag("backend").user(user).build());
    return user;
  }
}
