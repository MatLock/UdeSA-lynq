package com.lynq.analytics.service;

import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.exceptions.InvalidDomainEventException;
import com.lynq.analytics.exceptions.UnknownJobPostException;
import com.lynq.analytics.listener.message.DomainEventMessage;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.JobPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.lynq.analytics.service.JobPostProjector.JOB_POST_CLOSED;
import static com.lynq.analytics.service.JobPostProjector.JOB_POST_PUBLISHED;
import static com.lynq.analytics.service.JobPostProjector.JOB_POST_REOPENED;
import static com.lynq.analytics.service.JobPostProjector.JOB_POST_UPDATED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostProjectorTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final UUID EVENT_ID = UUID.fromString("5c8f3a3e-0b7e-5d61-9c1a-2f4b8e6d7a10");
  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final String COMPANY_ID = "22222222-2222-2222-2222-222222222222";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final Instant T1 = Instant.parse("2026-09-20T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-09-22T10:00:00Z");
  private static final Instant T3 = Instant.parse("2026-09-24T10:00:00Z");

  private static final String PUBLISHED = """
      {"jobId": "%s", "title": "Backend Developer", "category": "tecnologia",
       "workType": "REMOTE", "source": "LYNQ", "companyId": "%s", "createdByUserId": "%s",
       "salaryRangeDown": 1500000, "salaryRangeTop": 2200000, "salaryCurrency": "ARS",
       "skills": ["Java", " Spring ", "java", ""], "similarityTags": ["backend", "jvm"],
       "publishedOn": "2026-09-20", "synthetic": true, "notYetKnown": "ignored"}"""
      .formatted(JOB_ID, COMPANY_ID, USER_ID);

  private static final String UPDATED = """
      {"jobId": "%s", "title": "Senior Backend Developer", "workType": "IN_OFFICE",
       "salaryRangeDown": null, "salaryRangeTop": null, "salaryCurrency": null,
       "skills": ["Kotlin"], "similarityTags": null}""".formatted(JOB_ID);

  private static final String CLOSED = """
      {"jobId": "%s", "closedOn": "2026-09-24", "closeReason": "OWNER"}""".formatted(JOB_ID);

  private static final String REOPENED = """
      {"jobId": "%s", "reopenedOn": "2026-09-26"}""".formatted(JOB_ID);

  @Mock
  private JobPostRepository jobPostRepository;

  private JobPostProjector jobPostProjector;

  @BeforeEach
  void setUp() {
    jobPostProjector = new JobPostProjector(jobPostRepository, JSON);
  }

  @Test
  void supportsTheFourJobPostEvents() {
    assertThat(jobPostProjector.supports(JOB_POST_PUBLISHED), is(true));
    assertThat(jobPostProjector.supports(JOB_POST_UPDATED), is(true));
    assertThat(jobPostProjector.supports(JOB_POST_CLOSED), is(true));
    assertThat(jobPostProjector.supports(JOB_POST_REOPENED), is(true));
    assertThat(jobPostProjector.supports("ApplicationSubmitted"), is(false));
  }

  @Test
  void publishedCreatesTheJobPostOpenWithItsSkillsAndTags() {
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.empty());

    jobPostProjector.project(message(JOB_POST_PUBLISHED, T1, PUBLISHED));

    JobPostEntity job = saved();
    assertThat(job.getId(), is(JOB_ID));
    assertThat(job.getTitle(), is("Backend Developer"));
    assertThat(job.getCategory(), is("tecnologia"));
    assertThat(job.getWorkType(), is("REMOTE"));
    assertThat(job.getSource(), is("LYNQ"));
    assertThat(job.getCompanyId(), is(COMPANY_ID));
    assertThat(job.getCreatedByUserId(), is(USER_ID));
    assertThat(job.getSalaryRangeDown(), is(1500000));
    assertThat(job.getSalaryRangeTop(), is(2200000));
    assertThat(job.getSalaryCurrency(), is("ARS"));
    assertThat(job.getPublishedOn(), is(LocalDate.parse("2026-09-20")));
    assertThat(job.isSynthetic(), is(true));
    assertThat(job.getStatus(), is(JobStatus.OPEN));
    assertThat(job.getClosedOn(), is(nullValue()));
    assertThat(job.getSkills(), containsInAnyOrder("Java", "Spring"));
    assertThat(job.getTags(), containsInAnyOrder("backend", "jvm"));
    assertThat(job.getDetailsOccurredOn(), is(T1));
    assertThat(job.getStatusOccurredOn(), is(T1));
  }

  @Test
  void publishedWithoutSyntheticIsNotSynthetic() {
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.empty());

    jobPostProjector.project(message(JOB_POST_PUBLISHED, T1, """
        {"jobId": "%s", "title": "Analista", "workType": "REMOTE", "source": "BUMERAN",
         "publishedOn": "2026-09-20"}""".formatted(JOB_ID)));

    JobPostEntity job = saved();
    assertThat(job.isSynthetic(), is(false));
    assertThat(job.getSkills(), is(empty()));
    assertThat(job.getTags(), is(empty()));
  }

  @Test
  void publishedOlderThanAStoredCloseRefreshesTheDetailsButKeepsTheJobClosed() {
    JobPostEntity job = closedJob(T1, T3);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    jobPostProjector.project(message(JOB_POST_PUBLISHED, T2, PUBLISHED));

    assertThat(saved(), is(sameInstance(job)));
    assertThat(job.getTitle(), is("Backend Developer"));
    assertThat(job.getDetailsOccurredOn(), is(T2));
    assertThat(job.getStatus(), is(JobStatus.CLOSE));
    assertThat(job.getStatusOccurredOn(), is(T3));
  }

  @Test
  void publishedOlderThanEverythingStoredIsDiscarded() {
    JobPostEntity job = closedJob(T3, T3);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    jobPostProjector.project(message(JOB_POST_PUBLISHED, T1, PUBLISHED));

    verify(jobPostRepository, never()).save(any());
    assertThat(job.getTitle(), is("Stored title"));
  }

  @Test
  void updatedReplacesTheEditableFieldsSkillsAndTags() {
    JobPostEntity job = openJob(T1, T1);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    jobPostProjector.project(message(JOB_POST_UPDATED, T2, UPDATED));

    assertThat(saved(), is(sameInstance(job)));
    assertThat(job.getTitle(), is("Senior Backend Developer"));
    assertThat(job.getWorkType(), is("IN_OFFICE"));
    assertThat(job.getSalaryRangeDown(), is(nullValue()));
    assertThat(job.getSalaryRangeTop(), is(nullValue()));
    assertThat(job.getSalaryCurrency(), is(nullValue()));
    assertThat(job.getSkills(), containsInAnyOrder("Kotlin"));
    assertThat(job.getTags(), is(empty()));
    assertThat(job.getSource(), is("LYNQ"));
    assertThat(job.getCategory(), is("tecnologia"));
    assertThat(job.getDetailsOccurredOn(), is(T2));
    assertThat(job.getStatusOccurredOn(), is(T1));
  }

  @Test
  void updatedOlderThanTheStoredDetailsIsDiscarded() {
    JobPostEntity job = openJob(T3, T1);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    jobPostProjector.project(message(JOB_POST_UPDATED, T2, UPDATED));

    verify(jobPostRepository, never()).save(any());
    assertThat(job.getTitle(), is("Stored title"));
    assertThat(job.getDetailsOccurredOn(), is(T3));
  }

  @Test
  void updatedAtTheSameInstantAsTheStoredDetailsIsApplied() {
    JobPostEntity job = openJob(T2, T1);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    jobPostProjector.project(message(JOB_POST_UPDATED, T2, UPDATED));

    assertThat(saved().getTitle(), is("Senior Backend Developer"));
  }

  @Test
  void updatedForAJobPostNotPublishedYetIsRetried() {
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.empty());

    UnknownJobPostException exception = assertThrows(UnknownJobPostException.class,
        () -> jobPostProjector.project(message(JOB_POST_UPDATED, T2, UPDATED)));

    assertThat(exception.getMessage(), is("Domain event '" + EVENT_ID + "' of type '"
        + JOB_POST_UPDATED + "' is for job post '" + JOB_ID + "', which has not been published yet"));
    verify(jobPostRepository, never()).save(any());
  }

  @Test
  void closedClosesTheJobPostWithItsDateAndReason() {
    JobPostEntity job = openJob(T1, T1);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    jobPostProjector.project(message(JOB_POST_CLOSED, T3, CLOSED));

    assertThat(saved(), is(sameInstance(job)));
    assertThat(job.getStatus(), is(JobStatus.CLOSE));
    assertThat(job.getClosedOn(), is(LocalDate.parse("2026-09-24")));
    assertThat(job.getCloseReason(), is("OWNER"));
    assertThat(job.getStatusOccurredOn(), is(T3));
    assertThat(job.getDetailsOccurredOn(), is(T1));
  }

  @Test
  void closedIsAppliedEvenWhenANewerUpdateWasAlreadyProjected() {
    JobPostEntity job = openJob(T3, T1);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    jobPostProjector.project(message(JOB_POST_CLOSED, T2, CLOSED));

    assertThat(saved().getStatus(), is(JobStatus.CLOSE));
  }

  @Test
  void closedWithoutReasonLeavesItEmpty() {
    JobPostEntity job = openJob(T1, T1);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    jobPostProjector.project(message(JOB_POST_CLOSED, T2, """
        {"jobId": "%s", "closedOn": "2026-09-22"}""".formatted(JOB_ID)));

    assertThat(saved().getCloseReason(), is(nullValue()));
  }

  @Test
  void closedOlderThanAStoredReopenIsDiscarded() {
    JobPostEntity job = openJob(T1, T3);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    jobPostProjector.project(message(JOB_POST_CLOSED, T2, CLOSED));

    verify(jobPostRepository, never()).save(any());
    assertThat(job.getStatus(), is(JobStatus.OPEN));
  }

  @Test
  void reopenedOpensTheJobPostAndClearsTheClose() {
    JobPostEntity job = closedJob(T1, T2);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    jobPostProjector.project(message(JOB_POST_REOPENED, T3, REOPENED));

    assertThat(saved(), is(sameInstance(job)));
    assertThat(job.getStatus(), is(JobStatus.OPEN));
    assertThat(job.getReopenedOn(), is(LocalDate.parse("2026-09-26")));
    assertThat(job.getClosedOn(), is(nullValue()));
    assertThat(job.getCloseReason(), is(nullValue()));
    assertThat(job.getPublishedOn(), is(LocalDate.parse("2026-09-20")));
    assertThat(job.getStatusOccurredOn(), is(T3));
  }

  @Test
  void reopenedOlderThanTheStoredCloseIsDiscarded() {
    JobPostEntity job = closedJob(T1, T3);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    jobPostProjector.project(message(JOB_POST_REOPENED, T2, REOPENED));

    verify(jobPostRepository, never()).save(any());
    assertThat(job.getStatus(), is(JobStatus.CLOSE));
  }

  @Test
  void closedForAJobPostNotPublishedYetIsRetried() {
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.empty());

    assertThrows(UnknownJobPostException.class,
        () -> jobPostProjector.project(message(JOB_POST_CLOSED, T2, CLOSED)));
  }

  @Test
  void rejectsAPayloadWhoseJobIdIsNotTheAggregateId() {
    assertRejected(message(JOB_POST_CLOSED, T2, """
            {"jobId": "88888888-8888-8888-8888-888888888888", "closedOn": "2026-09-24"}"""),
        "has jobId '88888888-8888-8888-8888-888888888888' but aggregateId '" + JOB_ID + "'");
  }

  @Test
  void rejectsAPayloadWithoutJobId() {
    assertRejected(message(JOB_POST_REOPENED, T2, """
        {"reopenedOn": "2026-09-26"}"""), "has no jobId");
  }

  @Test
  void rejectsAPublishedWithoutTitle() {
    assertRejected(message(JOB_POST_PUBLISHED, T1, """
        {"jobId": "%s", "workType": "REMOTE", "source": "LYNQ", "publishedOn": "2026-09-20"}"""
        .formatted(JOB_ID)), "has no title");
  }

  @Test
  void rejectsAPublishedWithoutSource() {
    assertRejected(message(JOB_POST_PUBLISHED, T1, """
        {"jobId": "%s", "title": "Analista", "workType": "REMOTE", "publishedOn": "2026-09-20"}"""
        .formatted(JOB_ID)), "has no source");
  }

  @Test
  void rejectsAPublishedWithoutPublishedOn() {
    assertRejected(message(JOB_POST_PUBLISHED, T1, """
        {"jobId": "%s", "title": "Analista", "workType": "REMOTE", "source": "LYNQ"}"""
        .formatted(JOB_ID)), "has no publishedOn");
  }

  @Test
  void rejectsAnUpdatedWithoutWorkType() {
    assertRejected(message(JOB_POST_UPDATED, T2, """
        {"jobId": "%s", "title": "Analista"}""".formatted(JOB_ID)), "has no workType");
  }

  @Test
  void rejectsAClosedWithoutClosedOn() {
    assertRejected(message(JOB_POST_CLOSED, T2, """
        {"jobId": "%s", "closeReason": "OWNER"}""".formatted(JOB_ID)), "has no closedOn");
  }

  @Test
  void rejectsAReopenedWithoutReopenedOn() {
    assertRejected(message(JOB_POST_REOPENED, T2, """
        {"jobId": "%s"}""".formatted(JOB_ID)), "has no reopenedOn");
  }

  @Test
  void rejectsAPayloadThatCannotBeRead() {
    InvalidDomainEventException exception = assertThrows(InvalidDomainEventException.class,
        () -> jobPostProjector.project(message(JOB_POST_CLOSED, T2, """
            {"jobId": "%s", "closedOn": "yesterday"}""".formatted(JOB_ID))));

    assertThat(exception.getMessage().startsWith("Domain event '" + EVENT_ID + "' of type '"
        + JOB_POST_CLOSED + "' has a payload that cannot be read: "), is(true));
    verifyNoInteractions(jobPostRepository);
  }

  @Test
  void refusesAnEventTypeItDoesNotSupport() {
    assertThrows(IllegalArgumentException.class,
        () -> jobPostProjector.project(message("ApplicationSubmitted", T1, CLOSED)));
  }

  private void assertRejected(DomainEventMessage message, String problem) {
    InvalidDomainEventException exception = assertThrows(InvalidDomainEventException.class,
        () -> jobPostProjector.project(message));

    assertThat(exception.getMessage(), is("Domain event '" + EVENT_ID + "' of type '"
        + message.eventType() + "' " + problem));
    verifyNoInteractions(jobPostRepository);
  }

  private JobPostEntity saved() {
    ArgumentCaptor<JobPostEntity> captor = ArgumentCaptor.forClass(JobPostEntity.class);
    verify(jobPostRepository).save(captor.capture());
    return captor.getValue();
  }

  private JobPostEntity openJob(Instant detailsOccurredOn, Instant statusOccurredOn) {
    return JobPostEntity.builder()
        .id(JOB_ID)
        .title("Stored title")
        .category("tecnologia")
        .workType("REMOTE")
        .source("LYNQ")
        .status(JobStatus.OPEN)
        .publishedOn(LocalDate.parse("2026-09-20"))
        .skills(new HashSet<>(Set.of("Java")))
        .tags(new HashSet<>(Set.of("backend")))
        .detailsOccurredOn(detailsOccurredOn)
        .statusOccurredOn(statusOccurredOn)
        .build();
  }

  private JobPostEntity closedJob(Instant detailsOccurredOn, Instant statusOccurredOn) {
    JobPostEntity job = openJob(detailsOccurredOn, statusOccurredOn);
    job.setStatus(JobStatus.CLOSE);
    job.setClosedOn(LocalDate.parse("2026-09-22"));
    job.setCloseReason("OWNER");
    return job;
  }

  private DomainEventMessage message(String eventType, Instant occurredOn, String payload) {
    return new DomainEventMessage(EVENT_ID, eventType, "JOB_POST", JOB_ID, occurredOn,
        JSON.readTree(payload));
  }
}
