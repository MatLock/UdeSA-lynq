package com.lynq.analytics.service;

import com.lynq.analytics.exceptions.InvalidDomainEventException;
import com.lynq.analytics.exceptions.UnknownJobPostException;
import com.lynq.analytics.listener.message.DomainEventMessage;
import com.lynq.analytics.model.ApplicationEntity;
import com.lynq.analytics.repository.ApplicationRepository;
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
import java.util.Optional;
import java.util.UUID;

import static com.lynq.analytics.service.ApplicationProjector.APPLICATION_SUBMITTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationProjectorTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final UUID EVENT_ID = UUID.fromString("5c8f3a3e-0b7e-5d61-9c1a-2f4b8e6d7a10");
  private static final String APPLICATION_ID = "33333333-3333-3333-3333-333333333333";
  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final Instant T1 = Instant.parse("2026-09-20T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-09-22T10:00:00Z");

  private static final String SUBMITTED = """
      {"applicationId": "%s", "jobId": "%s", "userId": "%s", "appliedOn": "2026-09-22",
       "lynqScore": 72, "synthetic": true, "notYetKnown": "ignored"}"""
      .formatted(APPLICATION_ID, JOB_ID, USER_ID);

  @Mock
  private ApplicationRepository applicationRepository;

  @Mock
  private JobPostRepository jobPostRepository;

  private ApplicationProjector applicationProjector;

  @BeforeEach
  void setUp() {
    applicationProjector = new ApplicationProjector(applicationRepository, jobPostRepository, JSON);
  }

  @Test
  void supportsApplicationSubmitted() {
    assertThat(applicationProjector.supports(APPLICATION_SUBMITTED), is(true));
    assertThat(applicationProjector.supports("CandidateSkillsUpdated"), is(false));
  }

  @Test
  void submittedCreatesTheApplicationWithTheScoreOfTheEvent() {
    when(jobPostRepository.existsById(JOB_ID)).thenReturn(true);
    when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());

    applicationProjector.project(message(APPLICATION_SUBMITTED, T2, SUBMITTED));

    ApplicationEntity application = saved();
    assertThat(application.getId(), is(APPLICATION_ID));
    assertThat(application.getJobId(), is(JOB_ID));
    assertThat(application.getCandidateId(), is(USER_ID));
    assertThat(application.getAppliedOn(), is(LocalDate.parse("2026-09-22")));
    assertThat(application.getLynqScore(), is(72));
    assertThat(application.isSynthetic(), is(true));
    assertThat(application.getOccurredOn(), is(T2));
  }

  @Test
  void submittedWithoutSyntheticIsNotSynthetic() {
    when(jobPostRepository.existsById(JOB_ID)).thenReturn(true);
    when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());

    applicationProjector.project(message(APPLICATION_SUBMITTED, T2, """
        {"applicationId": "%s", "jobId": "%s", "userId": "%s", "appliedOn": "2026-09-22",
         "lynqScore": 0}""".formatted(APPLICATION_ID, JOB_ID, USER_ID)));

    assertThat(saved().isSynthetic(), is(false));
  }

  @Test
  void submittedAgainRefreshesTheStoredApplication() {
    ApplicationEntity application = storedApplication(T1);
    when(jobPostRepository.existsById(JOB_ID)).thenReturn(true);
    when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

    applicationProjector.project(message(APPLICATION_SUBMITTED, T2, SUBMITTED));

    assertThat(saved(), is(sameInstance(application)));
    assertThat(application.getLynqScore(), is(72));
    assertThat(application.getOccurredOn(), is(T2));
  }

  @Test
  void submittedOlderThanTheStoredApplicationIsDiscarded() {
    ApplicationEntity application = storedApplication(T2);
    when(jobPostRepository.existsById(JOB_ID)).thenReturn(true);
    when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

    applicationProjector.project(message(APPLICATION_SUBMITTED, T1, SUBMITTED));

    verify(applicationRepository, never()).save(any());
    assertThat(application.getLynqScore(), is(40));
  }

  @Test
  void submittedForAJobPostNotPublishedYetIsRetried() {
    when(jobPostRepository.existsById(JOB_ID)).thenReturn(false);

    UnknownJobPostException exception = assertThrows(UnknownJobPostException.class,
        () -> applicationProjector.project(message(APPLICATION_SUBMITTED, T2, SUBMITTED)));

    assertThat(exception.getMessage(), is("Domain event '" + EVENT_ID + "' of type '"
        + APPLICATION_SUBMITTED + "' is for job post '" + JOB_ID
        + "', which has not been published yet"));
    verifyNoInteractions(applicationRepository);
  }

  @Test
  void rejectsAPayloadWhoseApplicationIdIsNotTheAggregateId() {
    assertRejected(message(APPLICATION_SUBMITTED, T2, """
            {"applicationId": "44444444-4444-4444-4444-444444444444", "jobId": "%s",
             "userId": "%s", "appliedOn": "2026-09-22", "lynqScore": 72}"""
            .formatted(JOB_ID, USER_ID)),
        "has applicationId '44444444-4444-4444-4444-444444444444' but aggregateId '"
            + APPLICATION_ID + "'");
  }

  @Test
  void rejectsAPayloadWithoutJobId() {
    assertRejected(message(APPLICATION_SUBMITTED, T2, """
        {"applicationId": "%s", "userId": "%s", "appliedOn": "2026-09-22", "lynqScore": 72}"""
        .formatted(APPLICATION_ID, USER_ID)), "has no jobId");
  }

  @Test
  void rejectsAPayloadWithoutUserId() {
    assertRejected(message(APPLICATION_SUBMITTED, T2, """
        {"applicationId": "%s", "jobId": "%s", "appliedOn": "2026-09-22", "lynqScore": 72}"""
        .formatted(APPLICATION_ID, JOB_ID)), "has no userId");
  }

  @Test
  void rejectsAPayloadWithoutAppliedOn() {
    assertRejected(message(APPLICATION_SUBMITTED, T2, """
        {"applicationId": "%s", "jobId": "%s", "userId": "%s", "lynqScore": 72}"""
        .formatted(APPLICATION_ID, JOB_ID, USER_ID)), "has no appliedOn");
  }

  @Test
  void rejectsAPayloadWithoutLynqScore() {
    assertRejected(message(APPLICATION_SUBMITTED, T2, """
        {"applicationId": "%s", "jobId": "%s", "userId": "%s", "appliedOn": "2026-09-22"}"""
        .formatted(APPLICATION_ID, JOB_ID, USER_ID)), "has no lynqScore");
  }

  @Test
  void rejectsALynqScoreAboveOneHundred() {
    assertRejected(message(APPLICATION_SUBMITTED, T2, """
        {"applicationId": "%s", "jobId": "%s", "userId": "%s", "appliedOn": "2026-09-22",
         "lynqScore": 101}""".formatted(APPLICATION_ID, JOB_ID, USER_ID)),
        "has lynqScore 101, outside 0 to 100");
  }

  @Test
  void rejectsANegativeLynqScore() {
    assertRejected(message(APPLICATION_SUBMITTED, T2, """
        {"applicationId": "%s", "jobId": "%s", "userId": "%s", "appliedOn": "2026-09-22",
         "lynqScore": -1}""".formatted(APPLICATION_ID, JOB_ID, USER_ID)),
        "has lynqScore -1, outside 0 to 100");
  }

  @Test
  void rejectsAPayloadThatCannotBeRead() {
    InvalidDomainEventException exception = assertThrows(InvalidDomainEventException.class,
        () -> applicationProjector.project(message(APPLICATION_SUBMITTED, T2, """
            {"applicationId": "%s", "appliedOn": "yesterday"}""".formatted(APPLICATION_ID))));

    assertThat(exception.getMessage().startsWith("Domain event '" + EVENT_ID + "' of type '"
        + APPLICATION_SUBMITTED + "' has a payload that cannot be read: "), is(true));
    verifyNoInteractions(applicationRepository, jobPostRepository);
  }

  @Test
  void refusesAnEventTypeItDoesNotSupport() {
    assertThrows(IllegalArgumentException.class,
        () -> applicationProjector.project(message("JobPostClosed", T1, SUBMITTED)));
  }

  private void assertRejected(DomainEventMessage message, String problem) {
    InvalidDomainEventException exception = assertThrows(InvalidDomainEventException.class,
        () -> applicationProjector.project(message));

    assertThat(exception.getMessage(), is("Domain event '" + EVENT_ID + "' of type '"
        + message.eventType() + "' " + problem));
    verifyNoInteractions(applicationRepository, jobPostRepository);
  }

  private ApplicationEntity saved() {
    ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
    verify(applicationRepository).save(captor.capture());
    return captor.getValue();
  }

  private ApplicationEntity storedApplication(Instant occurredOn) {
    return ApplicationEntity.builder()
        .id(APPLICATION_ID)
        .jobId(JOB_ID)
        .candidateId(USER_ID)
        .appliedOn(LocalDate.parse("2026-09-21"))
        .lynqScore(40)
        .occurredOn(occurredOn)
        .build();
  }

  private DomainEventMessage message(String eventType, Instant occurredOn, String payload) {
    return new DomainEventMessage(EVENT_ID, eventType, "APPLICATION", APPLICATION_ID, occurredOn,
        JSON.readTree(payload));
  }
}
